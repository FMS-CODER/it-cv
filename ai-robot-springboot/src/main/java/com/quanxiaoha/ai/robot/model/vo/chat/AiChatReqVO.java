package com.quanxiaoha.ai.robot.model.vo.chat;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * @author: 犬小哈
 * @url: www.quanxiaoha.com
 * @date: 2023-09-15 14:07
 * @description: AI 聊天
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AiChatReqVO {

    @NotBlank(message = "用户消息不能为空")
    private String message;

    /**
     * 对话 ID
     */
    private String chatId;

    /**
     * 联网搜索
     */
    private Boolean networkSearch = false;

    /**
     * 是否启用简历知识库 RAG（双路向量检索后写入系统提示）
     */
    private Boolean knowledgeRag = false;

    /**
     * 知识库检索分类过滤（可选，空表示全部分类）
     */
    private String kbCategory;

    /**
     * 知识库合并检索 Top-K（可选，默认 5）
     */
    private Integer kbTopK = 5;

    @NotBlank(message = "调用的 AI 大模型名称不能为空")
    private String modelName;

    /**
     * 温度值，默认为 0.7
     */
    private Double temperature = 0.7;
}
