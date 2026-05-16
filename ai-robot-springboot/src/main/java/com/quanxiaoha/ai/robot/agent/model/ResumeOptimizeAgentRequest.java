package com.quanxiaoha.ai.robot.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 简历优化 Agent 请求参数 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeOptimizeAgentRequest {

    /** 链路追踪 ID */
    private String traceId;

    /** 简历原文 */
    private String resumeText;

    /** 目标岗位 */
    private String targetPosition;

    /** 附加要求 */
    private String additionalRequirements;

    /** 是否启用知识库 RAG */
    private boolean knowledgeRag;

    /** 知识库分类过滤 */
    private String kbCategory;

    /** 知识库检索 Top-K */
    @Builder.Default
    private int kbTopK = 5;

    /** 是否允许搜索工具 */
    private boolean searchToolEnabled;

    /** 是否启用 Planner */
    private boolean agentPlanner;

    /** 最大执行步数 */
    @Builder.Default
    private int maxAgentSteps = 3;
}
