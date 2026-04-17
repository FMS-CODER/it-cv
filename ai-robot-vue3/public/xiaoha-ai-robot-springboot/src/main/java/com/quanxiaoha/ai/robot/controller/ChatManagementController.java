package com.quanxiaoha.ai.robot.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天管理控制器
 * 提供对话的新建、查询、删除、重命名等功能
 */
@RestController
@RequestMapping("/chat")
public class ChatManagementController {

    private static final Logger log = LoggerFactory.getLogger(ChatManagementController.class);

    // 内存存储对话历史（生产环境建议使用数据库）
    private static final Map<String, ChatSession> chatSessions = new ConcurrentHashMap<>();
    private static final Map<String, List<ChatMessage>> chatMessages = new ConcurrentHashMap<>();

    /**
     * 新建对话
     */
    @PostMapping("/new")
    public ApiResponse newChat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        
        // 生成 UUID 作为对话 ID
        String chatId = UUID.randomUUID().toString();
        
        // 创建对话会话
        ChatSession session = new ChatSession();
        session.setUuid(chatId);
        session.setId(chatId);
        session.setSummary(message != null && message.length() > 20 
            ? message.substring(0, 20) + "..." 
            : (message != null ? message : "新对话"));
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        
        chatSessions.put(chatId, session);
        
        // 初始化消息列表
        chatMessages.put(chatId, new ArrayList<>());
        
        // 如果有初始消息，保存它
        if (message != null && !message.trim().isEmpty()) {
            ChatMessage userMessage = new ChatMessage();
            userMessage.setId(UUID.randomUUID().toString());
            userMessage.setChatId(chatId);
            userMessage.setContent(message);
            userMessage.setRole("user");
            userMessage.setCreatedAt(LocalDateTime.now());
            chatMessages.get(chatId).add(userMessage);
        }
        
        log.info("新建对话成功，chatId: {}", chatId);
        
        ApiResponse response = new ApiResponse();
        response.setSuccess(true);
        response.setData(session);
        
        return response;
    }

    /**
     * 查询历史对话列表（分页）
     */
    @PostMapping("/list")
    public ApiResponse listHistoryChat(
            @RequestBody Map<String, Integer> request) {
        Integer current = request.getOrDefault("current", 1);
        Integer size = request.getOrDefault("size", 10);
        
        List<ChatSession> allChats = new ArrayList<>(chatSessions.values());
        
        // 按更新时间倒序排序
        allChats.sort((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()));
        
        // 分页
        int total = allChats.size();
        int totalPages = (int) Math.ceil((double) total / size);
        int fromIndex = (current - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);
        
        List<ChatSession> pageData = fromIndex < total 
            ? allChats.subList(fromIndex, toIndex) 
            : new ArrayList<>();
        
        ApiResponse response = new ApiResponse();
        response.setSuccess(true);
        response.setData(pageData);
        response.setPages(totalPages);
        
        return response;
    }

    /**
     * 查询对话消息列表（分页）
     */
    @PostMapping("/message/list")
    public ApiResponse listChatMessages(
            @RequestBody Map<String, Object> request) {
        Integer current = (Integer) request.getOrDefault("current", 1);
        Integer size = (Integer) request.getOrDefault("size", 20);
        String chatId = (String) request.get("chatId");
        
        if (chatId == null || !chatMessages.containsKey(chatId)) {
            ApiResponse response = new ApiResponse();
            response.setSuccess(false);
            response.setMessage("对话不存在");
            return response;
        }
        
        List<ChatMessage> allMessages = chatMessages.get(chatId);
        
        // 分页
        int total = allMessages.size();
        int totalPages = (int) Math.ceil((double) total / size);
        int fromIndex = (current - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);
        
        List<ChatMessage> pageData = fromIndex < total 
            ? allMessages.subList(fromIndex, toIndex) 
            : new ArrayList<>();
        
        ApiResponse response = new ApiResponse();
        response.setSuccess(true);
        response.setData(pageData);
        response.setPages(totalPages);
        
        return response;
    }

    /**
     * 删除对话
     */
    @PostMapping("/delete")
    public ApiResponse deleteChat(@RequestBody Map<String, String> request) {
        String uuid = request.get("uuid");
        
        if (uuid == null || !chatSessions.containsKey(uuid)) {
            ApiResponse response = new ApiResponse();
            response.setSuccess(false);
            response.setMessage("对话不存在");
            return response;
        }
        
        chatSessions.remove(uuid);
        chatMessages.remove(uuid);
        
        log.info("删除对话成功，uuid: {}", uuid);
        
        ApiResponse response = new ApiResponse();
        response.setSuccess(true);
        
        return response;
    }

    /**
     * 重命名对话
     */
    @PostMapping("/summary/rename")
    public ApiResponse renameChat(@RequestBody Map<String, String> request) {
        String id = request.get("id");
        String summary = request.get("summary");
        
        if (id == null || !chatSessions.containsKey(id)) {
            ApiResponse response = new ApiResponse();
            response.setSuccess(false);
            response.setMessage("对话不存在");
            return response;
        }
        
        ChatSession session = chatSessions.get(id);
        session.setSummary(summary);
        session.setUpdatedAt(LocalDateTime.now());
        
        log.info("重命名对话成功，id: {}, summary: {}", id, summary);
        
        ApiResponse response = new ApiResponse();
        response.setSuccess(true);
        
        return response;
    }

    // ==================== 内部类 ====================

    /**
     * 对话会话
     */
    static class ChatSession {
        private String uuid;
        private String id;
        private String summary;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public String getUuid() { return uuid; }
        public void setUuid(String uuid) { this.uuid = uuid; }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    /**
     * 对话消息
     */
    static class ChatMessage {
        private String id;
        private String chatId;
        private String content;
        private String role;
        private LocalDateTime createdAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getChatId() { return chatId; }
        public void setChatId(String chatId) { this.chatId = chatId; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    /**
     * API 统一返回结果（适配前端期望的格式）
     */
    static class ApiResponse {
        private Boolean success;
        private String message;
        private Object data;
        private Integer pages;

        public Boolean getSuccess() { return success; }
        public void setSuccess(Boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Object getData() { return data; }
        public void setData(Object data) { this.data = data; }
        public Integer getPages() { return pages; }
        public void setPages(Integer pages) { this.pages = pages; }
    }
}
