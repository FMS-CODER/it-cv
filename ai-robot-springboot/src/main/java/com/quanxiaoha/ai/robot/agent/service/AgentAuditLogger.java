package com.quanxiaoha.ai.robot.agent.service;

import com.quanxiaoha.ai.robot.agent.model.AgentContext;
import com.quanxiaoha.ai.robot.agent.model.AgentStepTrace;
import com.quanxiaoha.ai.robot.agent.model.PlannerDecision;
import com.quanxiaoha.ai.robot.metrics.AgentMetricsService;
import com.quanxiaoha.ai.robot.metrics.AgentRunMetrics;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Agent 级日志记录器。
 */
@Service
@Slf4j
public class AgentAuditLogger {

    @Resource
    private AgentMetricsService agentMetricsService;

    public void recordRunStart(AgentContext context) {
        if (StringUtils.isBlank(context.getRequestId())) {
            context.setRequestId(UUID.randomUUID().toString());
        }
        context.setStartedAtMs(System.currentTimeMillis());
        log.info("Agent run start: requestId={}, scene={}, knowledgeRag={}, networkSearch={}, searchToolEnabled={}, maxAgentSteps={}",
                context.getRequestId(),
                context.getScene(),
                context.isKnowledgeRag(),
                context.isNetworkSearch(),
                context.isSearchToolEnabled(),
                context.getMaxAgentSteps());
    }

    public void recordStep(AgentContext context, String stepName, long durationMs, String summary) {
        context.getStepTraces().add(AgentStepTrace.builder()
                .stepName(stepName)
                .durationMs(durationMs)
                .summary(summary)
                .build());
        log.info("Agent step complete: scene={}, step={}, cost={}ms, summary={}",
                context.getScene(), stepName, durationMs, summary);
    }

    public void recordPlannerDecision(AgentContext context, PlannerDecision decision) {
        log.info("Agent planner decision: scene={}, intent={}, needKnowledgeSearch={}, needWebSearch={}, useSearchTools={}, toolQuery={}",
                context.getScene(),
                decision.getIntent(),
                decision.isNeedKnowledgeSearch(),
                decision.isNeedWebSearch(),
                decision.isUseSearchTools(),
                decision.getToolQuery());
    }

    public void recordPrompt(AgentContext context) {
        log.info("Agent prompt ready: scene={}, preview={}",
                context.getScene(),
                StringUtils.abbreviate(context.getFinalPromptPreview(), 800));
    }

    public void recordToolMode(AgentContext context, String mode) {
        context.setToolMode(mode);
        log.info("Agent tool mode: scene={}, mode={}", context.getScene(), mode);
    }

    public void recordFallback(AgentContext context, Throwable throwable) {
        context.setFallbackTriggered(true);
        log.warn("Agent tool calling fallback: scene={}, reason={}",
                context.getScene(), throwable == null ? "unknown" : throwable.getMessage(), throwable);
    }

    public void recordFirstToken(AgentContext context) {
        if (context.getStartedAtMs() <= 0 || context.getFirstTokenLatencyMs() > 0) {
            return;
        }
        long latency = Math.max(0L, System.currentTimeMillis() - context.getStartedAtMs());
        context.setFirstTokenLatencyMs(latency);
        log.info("Agent first token: requestId={}, scene={}, latency={}ms",
                context.getRequestId(), context.getScene(), latency);
    }

    public void recordRunFinished(AgentContext context, boolean success, String errorMessage) {
        context.setSuccess(success);
        context.setErrorMessage(errorMessage);
        long totalDurationMs = context.getStartedAtMs() <= 0 ? 0L : Math.max(0L, System.currentTimeMillis() - context.getStartedAtMs());
        context.setTotalDurationMs(totalDurationMs);
        AgentRunMetrics metrics = AgentRunMetrics.builder()
                .requestId(context.getRequestId())
                .scene(context.getScene())
                .startedAtMs(context.getStartedAtMs())
                .foundationDurationMs(context.getFoundationDurationMs())
                .plannerDurationMs(context.getPlannerDurationMs())
                .knowledgeSearchDurationMs(context.getKnowledgeSearchDurationMs())
                .webSearchDurationMs(context.getWebSearchDurationMs())
                .firstTokenLatencyMs(context.getFirstTokenLatencyMs())
                .totalDurationMs(totalDurationMs)
                .outputChunks(context.getOutputChunks())
                .outputChars(context.getOutputChars())
                .reasoningChars(context.getReasoningChars())
                .toolMode(context.getToolMode())
                .fallbackTriggered(context.isFallbackTriggered())
                .success(success)
                .errorMessage(errorMessage)
                .knowledgeRagEnabled(context.isKnowledgeRag())
                .networkSearchEnabled(context.isNetworkSearch())
                .searchToolEnabled(context.isSearchToolEnabled())
                .maxAgentSteps(context.getMaxAgentSteps())
                .stepTraces(new ArrayList<>(context.getStepTraces()))
                .build();
        agentMetricsService.saveRun(metrics);
        log.info("Agent run finish: requestId={}, scene={}, success={}, total={}ms, firstToken={}ms, chunks={}, outputChars={}, reasoningChars={}, fallback={}, toolMode={}, error={}",
                context.getRequestId(),
                context.getScene(),
                success,
                totalDurationMs,
                context.getFirstTokenLatencyMs(),
                context.getOutputChunks(),
                context.getOutputChars(),
                context.getReasoningChars(),
                context.isFallbackTriggered(),
                context.getToolMode(),
                StringUtils.defaultString(errorMessage));
    }
}
