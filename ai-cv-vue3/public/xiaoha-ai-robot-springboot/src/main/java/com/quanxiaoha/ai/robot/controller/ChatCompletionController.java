package com.quanxiaoha.ai.robot.controller;

import com.quanxiaoha.ai.robot.tools.SearchTools;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/chat")
public class ChatCompletionController {

    private static final Logger log = LoggerFactory.getLogger(ChatCompletionController.class);

    @Resource
    private ChatClient chatClient;

    private static final String GENERAL_SYSTEM_PROMPT = """
            你是一位专业、友好、知识渊博的 AI 助手。
            请用清晰、简洁、有条理的方式回答用户的问题。
            如果问题不明确，请先询问用户以获取更多信息。
            """;

    private static final String LEARNING_SYSTEM_PROMPT = """
            你是一位专业的学习规划师和教育顾问。
            你的主要职责是帮助用户制定科学、有效的学习计划，
            提供学习方法建议，解答学习过程中遇到的问题。

            请记住：
            1. 先了解用户的学习目标、时间安排、当前水平等信息
            2. 给出具体、可执行的学习建议
            3. 鼓励用户，给予积极的反馈
            4. 如果用户需要，可以分阶段制定学习计划
            """;

    private static final String RESUME_SYSTEM_PROMPT = """
            你是一位专业的简历优化师和职业规划顾问。
            你的主要职责是帮助用户优化简历，提供求职建议，
            解答职场相关问题。

            请记住：
            1. 如果用户询问简历相关问题，提供专业的简历优化建议
            2. 使用 STAR 法则描述工作经历
            3. 强调量化成果和数据
            4. 针对不同岗位提供针对性的建议
            5. 如果用户询问职场发展，提供职业规划建议
            """;

    @PostMapping(value = "/completion", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatCompletion(@RequestBody Map<String, Object> request) {
        String message = (String) request.get("message");
        String chatId = (String) request.get("chatId");
        Boolean networkSearch = (Boolean) request.get("networkSearch");
        String context = (String) request.get("context");
        
        log.info("收到聊天请求 - chatId: {}, message: {}, networkSearch: {}, context: {}", chatId, message, networkSearch, context);

        if (message == null || message.trim().isEmpty()) {
            return Flux.just(
                ServerSentEvent.builder("{\"v\":\"请输入消息内容\"}").build(),
                ServerSentEvent.builder("[DONE]").build()
            );
        }

        String systemPrompt = getSystemPrompt(context);
        log.info("使用的 System Prompt: {}", systemPrompt);

        ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                .system(systemPrompt)
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId));
        
        if (Boolean.TRUE.equals(networkSearch)) {
            log.info("启用联网搜索功能");
            spec = spec.tools(new SearchTools());
        }

        return spec.stream()
                .content()
                .map(content -> ServerSentEvent.builder(String.format("{\"v\":\"%s\"}", escapeJson(content))).build())
                .concatWithValues(ServerSentEvent.builder("[DONE]").build());
    }

    private String getSystemPrompt(String context) {
        if (context == null) {
            return GENERAL_SYSTEM_PROMPT;
        }
        switch (context) {
            case "learning":
                return LEARNING_SYSTEM_PROMPT;
            case "resume":
                return RESUME_SYSTEM_PROMPT;
            default:
                return GENERAL_SYSTEM_PROMPT;
        }
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
