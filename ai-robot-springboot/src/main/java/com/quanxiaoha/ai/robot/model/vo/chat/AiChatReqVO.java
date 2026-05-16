package com.quanxiaoha.ai.robot.model.vo.chat;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI 对话请求 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AiChatReqVO {

    /** 用户输入消息 */
    @NotBlank(message = "用户消息不能为空")
    private String message;

    /** 会话 UUID */
    private String chatId;

    /** 链路追踪 ID（可选） */
    private String traceId;

    /** 是否启用联网搜索 */
    @Builder.Default
    private Boolean networkSearch = false;

    /** 是否启用简历知识库 RAG */
    @Builder.Default
    private Boolean knowledgeRag = false;

    /** 知识库分类过滤（空=全部） */
    private String kbCategory;

    /** 知识库检索 Top-K，默认 5 */
    @Builder.Default
    private Integer kbTopK = 5;

    /** 是否允许 Agent 调用搜索工具 */
    @Builder.Default
    private Boolean searchToolEnabled = false;

    /** 是否启用 Planner 规划 */
    @Builder.Default
    private Boolean agentPlanner = false;

    /** Agent 最大执行步数 */
    @Builder.Default
    private Integer maxAgentSteps = 3;

    /** 大模型名称 */
    @NotBlank(message = "调用的 AI 大模型名称不能为空")
    private String modelName;

    /** 采样温度，默认 0.7 */
    @Builder.Default
    private Double temperature = 0.7;
}
