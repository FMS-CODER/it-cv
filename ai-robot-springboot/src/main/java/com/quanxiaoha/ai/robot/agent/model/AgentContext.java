package com.quanxiaoha.ai.robot.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 运行时上下文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentContext {

    private String requestId;

    private AgentScene scene;

    private String chatId;

    private String userInput;

    private String resumeText;

    private String targetPosition;

    private String additionalRequirements;

    private AgentIntent intent;

    private String modelName;

    private Double temperature;

    private boolean networkSearch;

    private boolean knowledgeRag;

    private String kbCategory;

    @Builder.Default
    private int kbTopK = 5;

    private boolean searchToolEnabled;

    private boolean agentPlannerEnabled;

    @Builder.Default
    private int maxAgentSteps = 3;

    private String ragContext;

    private PlannerDecision plannerDecision;

    private String systemPrompt;

    private String userPrompt;

    private String finalPromptPreview;

    private long startedAtMs;

    private long foundationDurationMs;

    private long plannerDurationMs;

    private long knowledgeSearchDurationMs;

    private long webSearchDurationMs;

    private long firstTokenLatencyMs;

    private long totalDurationMs;

    private int outputChunks;

    private int outputChars;

    private int reasoningChars;

    private String toolMode;

    private boolean fallbackTriggered;

    private boolean success;

    private String errorMessage;

    @Builder.Default
    private List<AgentStepTrace> stepTraces = new ArrayList<>();
}
