package com.quanxiaoha.ai.robot.metrics;

import com.quanxiaoha.ai.robot.agent.model.AgentScene;
import com.quanxiaoha.ai.robot.agent.model.AgentStepTrace;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 单次 Agent 运行指标快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunMetrics {

    private String requestId;

    private AgentScene scene;

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

    private boolean knowledgeRagEnabled;

    private boolean networkSearchEnabled;

    private boolean searchToolEnabled;

    private int maxAgentSteps;

    @Builder.Default
    private List<AgentStepTrace> stepTraces = new ArrayList<>();
}
