package com.quanxiaoha.ai.robot.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 简历优化 Agent 请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeOptimizeAgentRequest {

    private String traceId;

    private String resumeText;

    private String targetPosition;

    private String additionalRequirements;

    private boolean knowledgeRag;

    private String kbCategory;

    @Builder.Default
    private int kbTopK = 5;

    private boolean searchToolEnabled;

    private boolean agentPlanner;

    @Builder.Default
    private int maxAgentSteps = 3;
}
