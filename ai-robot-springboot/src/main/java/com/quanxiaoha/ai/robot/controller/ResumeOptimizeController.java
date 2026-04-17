package com.quanxiaoha.ai.robot.controller;

import jakarta.annotation.Resource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import com.quanxiaoha.ai.robot.service.ChatService;

/**
 * @Author: 小明
 * @Date: 2025/5/22 12:25
 * @Version: v1.0.0
 * @Description: 简历优化控制器
 **/
@RestController
@RequestMapping("/resume-optimize")
@CrossOrigin(origins = "http://localhost:5174", allowCredentials = "true")
public class ResumeOptimizeController {

    @Resource
    private ChatModel chatModel;

    @Resource
    private ChatClient chatClient;

    @Resource
    private ChatService chatService;

    /**
     * 上传简历文件
     * @param file 简历文件（PDF 或 DOCX）
     * @return 解析后的简历文本
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadResume(@RequestParam("file") MultipartFile file) {
        try {
            // 验证文件大小（50MB）
            long maxSize = 50 * 1024 * 1024; // 50MB
            if (file.getSize() > maxSize) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "文件大小不能超过 50MB"
                ));
            }

            // 验证文件类型
            String fileName = file.getOriginalFilename();
            if (fileName == null || (!fileName.endsWith(".pdf") && !fileName.endsWith(".docx"))) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "不支持的文件格式，请上传 PDF 或 DOCX 文件"
                ));
            }

            String text;
            if (fileName.endsWith(".pdf")) {
                // 使用 PDF 解析
                text = extractTextFromPdf(file);
            } else {
                // 使用 POI 解析 DOCX
                text = extractTextFromDocx(file);
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "text", text
            ));
            
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", "文件读取失败：" + e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", "处理失败：" + e.getMessage()
            ));
        }
    }

    /**
     * 从 PDF 文件中提取文本（PDF 为二进制，不能用 UTF-8 整包解码，否则乱码）
     */
    private String extractTextFromPdf(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            return text != null ? text.trim() : "";
        }
    }

    /**
     * 从 DOCX 文件中提取文本（DOCX 为 ZIP+XML，需用 POI 解析，不能当纯文本 UTF-8 读）
     */
    private String extractTextFromDocx(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : document.getParagraphs()) {
                String t = p.getText();
                if (t != null && !t.isBlank()) {
                    sb.append(t).append('\n');
                }
            }
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String c = cell.getText();
                        if (c != null) {
                            sb.append(c).append('\t');
                        }
                    }
                    sb.append('\n');
                }
            }
            return sb.toString().trim();
        }
    }

    /**
     * 优化简历（流式输出）
     * @param resumeText 简历文本
     * @param targetPosition 目标岗位
     * @param additionalRequirements 额外要求
     * @return 流式输出的优化建议
     */
    @PostMapping(value = "/optimize", produces = "text/event-stream;charset=utf-8")
    public Flux<String> optimizeResume(
            @RequestBody Map<String, String> params) {
        
        String resumeText = params.get("resumeText");
        String targetPosition = params.get("targetPosition");
        String additionalRequirements = params.get("additionalRequirements");
        
        // 构建提示词
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("你是一位专业的简历优化专家。\n\n");
        promptBuilder.append("请帮我优化以下简历，目标岗位是：").append(targetPosition).append("\n\n");
        if (additionalRequirements != null && !additionalRequirements.isEmpty()) {
            promptBuilder.append("额外要求：").append(additionalRequirements).append("\n\n");
        }
        promptBuilder.append("简历内容：\n").append(resumeText).append("\n\n");
        promptBuilder.append("请从以下几个方面提供优化建议：\n");
        promptBuilder.append("1. 简历结构和格式优化\n");
        promptBuilder.append("2. 工作内容描述优化（使用 STAR 法则）\n");
        promptBuilder.append("3. 技能亮点突出\n");
        promptBuilder.append("4. 项目经验优化\n");
        promptBuilder.append("5. 整体建议\n\n");
        promptBuilder.append("请用 Markdown 格式输出，保持专业、详细的风格。");
        
        Prompt prompt = new Prompt(new UserMessage(promptBuilder.toString()));
        
        // 流式输出
        return chatModel.stream(prompt)
                .map(chatResponse -> chatResponse.getResult().getOutput().getText())
                .map(text -> {
                    // 返回 JSON 格式，Spring 会自动添加 data: 前缀
                    return "{\"v\": \"" + escapeJson(text) + "\"}";
                });
    }

    /**
     * 智能对话（流式输出）
     * @param message 用户消息
     * @param chatId 会话 ID
     * @param enableSearch 是否启用搜索
     * @return 流式输出的回复
     */
    @PostMapping(value = "/chat", produces = "text/event-stream;charset=utf-8")
    public Flux<String> chat(
            @RequestBody Map<String, Object> params) {
        
        String message = (String) params.get("message");
        String chatId = (String) params.get("chatId");
        Boolean enableSearch = (Boolean) params.get("enableSearch");
        
        // 构建系统提示词
        String systemPrompt = "你是一位专业的简历优化和职业发展顾问，专注于以下领域：\n" +
                "1. 简历优化和修改建议\n" +
                "2. 面试准备和技巧\n" +
                "3. 职业发展规划\n" +
                "4. 技能提升建议\n" +
                "5. 求职策略\n\n" +
                "请用专业、友好、详细的风格回答用户的问题。" +
                "如果用户的问题与简历、求职、职业发展无关，请礼貌地引导用户回到这些主题。" +
                (enableSearch != null && enableSearch ? " 你可以结合最新的行业动态和趋势来回答问题。" : "");
        
        // 使用 ChatClient 带记忆的对话
        return chatClient.prompt()
                .system(systemPrompt)
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content()
                .map(text -> {
                    // 返回 JSON 格式，Spring 会自动添加 data: 前缀
                    return "{\"v\": \"" + escapeJson(text) + "\"}";
                });
    }

    /**
     * 转义 JSON 特殊字符
     */
    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    /**
     * 新建对话
     */
    @PostMapping("/chat/new")
    public ResponseEntity<Map<String, Object>> newChat() {
        try {
            String chatId = UUID.randomUUID().toString();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "chatId", chatId
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", "创建对话失败：" + e.getMessage()
            ));
        }
    }

    /**
     * 获取历史对话列表
     */
    @GetMapping("/chat/history")
    public Map<String, Object> getChatHistory() {
        // 暂时返回空列表，后续可以从数据库查询
        return Map.of(
            "success", true,
            "list", new java.util.ArrayList<>()
        );
    }

    /**
     * 删除对话
     */
    @DeleteMapping("/chat/{chatId}")
    public ResponseEntity<Map<String, Object>> deleteChat(@PathVariable String chatId) {
        try {
            // 暂时只返回成功，后续可以实现真实的删除逻辑
            return ResponseEntity.ok(Map.of(
                "success", true
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", "删除对话失败：" + e.getMessage()
            ));
        }
    }
}
