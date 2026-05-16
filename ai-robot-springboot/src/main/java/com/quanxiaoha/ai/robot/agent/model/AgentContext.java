package com.quanxiaoha.ai.robot.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Agent 单次请求运行时上下文 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentContext {

    /** 请求唯一 ID */
    private String requestId;

    /** 业务场景 */
    private AgentScene scene;

    /** 会话 UUID */
    private String chatId;

    /** 用户输入 */
    private String userInput;

    /** 简历原文（优化场景） */
    private String resumeText;

    /** 目标岗位 */
    private String targetPosition;

    /** 附加优化要求 */
    private String additionalRequirements;

    /** 识别出的用户意图 */
    private AgentIntent intent;

    /** 大模型名称 */
    private String modelName;

    /** 采样温度 */
    private Double temperature;

    /** 是否联网搜索 */
    private boolean networkSearch;

    /** 是否启用知识库 RAG */
    private boolean knowledgeRag;

    /** 知识库分类过滤 */
    private String kbCategory;

    /** 知识库检索 Top-K */
    @Builder.Default
    private int kbTopK = 5;

    /** 是否允许调用搜索工具 */
    private boolean searchToolEnabled;

    /** 是否启用 Planner */
    private boolean agentPlannerEnabled;

    /** 最大执行步数 */
    @Builder.Default
    private int maxAgentSteps = 3;

    /** RAG 检索拼接后的上下文 */
    private String ragContext;

    /** Planner 决策结果 */
    private PlannerDecision plannerDecision;

    /** 系统提示词 */
    private String systemPrompt;

    /** 用户侧提示词 */
    private String userPrompt;

    /** 最终送入模型的提示预览 */
    private String finalPromptPreview;

    /** 开始时间戳（毫秒） */
    private long startedAtMs;

    /** 基础准备阶段耗时（毫秒） */
    private long foundationDurationMs;

    /** Planner 阶段耗时（毫秒） */
    private long plannerDurationMs;

    /** 知识库检索耗时（毫秒） */
    private long knowledgeSearchDurationMs;

    /** 联网搜索耗时（毫秒） */
    private long webSearchDurationMs;

    /** 首 token 延迟（毫秒） */
    private long firstTokenLatencyMs;

    /** 总耗时（毫秒） */
    private long totalDurationMs;

    /** 流式输出块数 */
    private int outputChunks;

    /** 输出正文字符数 */
    private int outputChars;

    /** 推理内容字符数 */
    private int reasoningChars;

    /** 工具调用模式标识 */
    private String toolMode;

    /** 是否触发降级 */
    private boolean fallbackTriggered;

    /** 是否执行成功 */
    private boolean success;

    /** 失败原因 */
    private String errorMessage;

    /** 各步骤执行轨迹 */
    @Builder.Default
    private List<AgentStepTrace> stepTraces = new ArrayList<>();

    /** ReAct 多轮轨迹 */
    @Builder.Default
    private List<ReActStep> reactTraces = new ArrayList<>();

    /** 当前 ReAct 执行到第几步 */
    private int currentReactStep;

    /** ReAct 是否结束 */
    private boolean reactFinished;

    /** ReAct 最终回答 */
    private String reactFinalAnswer;
}
