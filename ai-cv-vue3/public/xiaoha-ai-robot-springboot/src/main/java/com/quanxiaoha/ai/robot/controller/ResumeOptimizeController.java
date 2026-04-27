package com.quanxiaoha.ai.robot.controller;

import com.quanxiaoha.ai.robot.dto.ResumeOptimizeRequest;
import com.quanxiaoha.ai.robot.service.ResumeKnowledgeService;
import com.quanxiaoha.ai.robot.tools.ResumeTools;
import com.quanxiaoha.ai.robot.tools.SearchTools;
import com.quanxiaoha.ai.robot.util.ResumeTextExtractor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/resume-optimize")
public class ResumeOptimizeController {

    private static final Logger log = LoggerFactory.getLogger(ResumeOptimizeController.class);

    @Resource
    private ChatClient chatClient;

    @Resource
    private ResumeKnowledgeService resumeKnowledgeService;

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是一位专业的简历优化师和职业发展顾问，擅长帮助用户优化简历、准备面试、规划职业发展。

            你的核心能力：
            1. 根据用户的背景和目标岗位，提供简历优化建议
            2. 帮助用户准备面试，提供常见问题和回答技巧
            3. 提供职业发展建议和薪资谈判指导
            4. 解答职场相关问题

            【重要】你可以调用以下工具来帮助用户：
            - getResumeOptimizationTips()：获取简历优化建议，针对不同岗位提供针对性建议
            - getInterviewPreparationTips()：获取面试准备建议，包括常见问题和回答技巧
            - getTechnicalInterviewTopics()：获取技术面试知识点清单
            - getCareerAdvice()：获取职场发展建议，根据不同级别提供针对性指导
            - getResumeChecklist()：生成简历检查清单，帮助用户自查
            - analyzeJobMatch()：分析简历与岗位的匹配度
            - generateStarTemplate()：生成 STAR 法则描述模板
            - generateSelfIntroduction()：生成面试自我介绍模板
            - analyzeSalary()：分析薪资竞争力，给出市场薪资建议
            - predictInterviewQuestions()：预测面试问题
            - generateResignationReasons()：生成离职原因模板

            【工作流程】
            1. 首先了解用户的背景（工作年限、技术栈、目标岗位）
            2. 根据用户需求调用相应工具
            3. 提供具体、可操作的建议
            4. 适当时候主动提供面试预测、薪资分析等增值服务

            以下是相关的知识库内容供你参考：
            {context}

            请用友好、专业、鼓励的语气回复，使用 Markdown 格式让回答更清晰易读。
            建议包含：具体建议、示例模板、注意事项等部分。
            """;

    private static final String RESUME_OPTIMIZE_PROMPT_TEMPLATE = """
            你是一位专业的简历优化师，擅长根据岗位要求优化简历。

            【知识库参考内容】
            %s

            【原简历内容】
            %s

            【目标岗位】
            %s

            【额外要求】
            %s

            请按照以下要求优化简历：
            1. 使用 STAR 法则描述工作经历，但要将整个经历（包括S-T-A-R四个部分）放在同一段落内，不要分段展示
            2. 突出量化成果（数据说话）
            3. 贴合目标岗位要求
            4. 语言专业简洁，使用动词开头
            5. 去除冗余信息，突出重点
            6. 保持简历在 1-2 页

            请输出优化后的完整简历，使用 Markdown 格式。
            """;

    @PostConstruct
    public void init() {
        log.info("简历优化助手初始化完成");
    }

    @PostMapping("/upload")
    public Map<String, Object> uploadResume(@RequestParam("file") MultipartFile file) {
        log.info("========================================");
        log.info("收到上传简历文件请求");
        log.info("文件名: {}", file.getOriginalFilename());
        log.info("文件大小: {} bytes", file.getSize());
        log.info("文件类型: {}", file.getContentType());
        log.info("========================================");

        Map<String, Object> result = new HashMap<>();
        try {
            log.info("开始解析文件...");
            String text = ResumeTextExtractor.extractText(file);
            log.info("文件解析成功，文本长度: {}", text.length());
            log.info("提取的文本前100字符: {}", text.length() > 100 ? text.substring(0, 100) : text);

            result.put("success", true);
            result.put("text", text);
            result.put("filename", file.getOriginalFilename());
            log.info("返回成功响应");
        } catch (Exception e) {
            log.error("========================================");
            log.error("简历解析失败", e);
            log.error("错误信息: {}", e.getMessage());
            log.error("========================================");
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @PostMapping(value = "/optimize", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> optimizeResume(@RequestBody ResumeOptimizeRequest request) {
        log.info("简历优化请求 - 岗位: {}, 文本长度: {}", 
                request.getTargetPosition(), 
                request.getResumeText() != null ? request.getResumeText().length() : 0);

        if (request.getResumeText() == null || request.getResumeText().trim().isEmpty()) {
            return Flux.just(
                ServerSentEvent.builder("{\"v\":\"请先上传简历或粘贴简历内容\"}").build(),
                ServerSentEvent.builder("[DONE]").build()
            );
        }

        String searchQuery = request.getTargetPosition() + " " + request.getResumeText();
        List<String> similarDocs = resumeKnowledgeService.searchSimilar(searchQuery, 5);
        String knowledge = String.join("\n\n", similarDocs);

        String additionalRequirements = request.getAdditionalRequirements() != null ? 
                request.getAdditionalRequirements() : "无";

        String prompt = String.format(RESUME_OPTIMIZE_PROMPT_TEMPLATE,
                knowledge,
                request.getResumeText(),
                request.getTargetPosition(),
                additionalRequirements);

        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .map(content -> ServerSentEvent.builder(String.format("{\"v\":\"%s\"}", escapeJson(content))).build())
                .concatWithValues(ServerSentEvent.builder("[DONE]").build());
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@RequestBody Map<String, Object> request) {
        String message = (String) request.get("message");
        String chatId = (String) request.get("chatId");
        Boolean enableSearch = (Boolean) request.getOrDefault("enableSearch", false);

        log.info("简历优化请求 - chatId: {}, message: {}, enableSearch: {}", chatId, message, enableSearch);

        if (message == null || message.trim().isEmpty()) {
            return Flux.just(
                ServerSentEvent.builder("{\"v\":\"你好！我是简历优化助手，可以帮你优化简历、准备面试、提供职业建议。请告诉我你的需求，比如：\\n\\n• 帮我优化一下 Java 后端开发的简历\\n• 我要面试前端岗位，该怎么准备\\n• 工作 3 年了，职业发展很迷茫\"}").build(),
                ServerSentEvent.builder("[DONE]").build()
            );
        }

        List<String> similarDocs = resumeKnowledgeService.searchSimilar(message, 3);
        String context = String.join("\n\n", similarDocs);
        
        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.replace("{context}", context);

        ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                .system(systemPrompt)
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId != null ? chatId : "resume-optimize-default"))
                .tools(new ResumeTools());

        if (Boolean.TRUE.equals(enableSearch)) {
            log.info("启用联网搜索功能");
            spec = spec.tools(new SearchTools());
        }

        return spec.stream()
                .content()
                .map(content -> ServerSentEvent.builder(String.format("{\"v\":\"%s\"}", escapeJson(content))).build())
                .concatWithValues(ServerSentEvent.builder("[DONE]").build());
    }

    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}