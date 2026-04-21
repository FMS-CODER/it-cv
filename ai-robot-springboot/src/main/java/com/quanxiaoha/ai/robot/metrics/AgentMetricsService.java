package com.quanxiaoha.ai.robot.metrics;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维护最近运行指标与最近一次评测结果。
 */
@Service
public class AgentMetricsService {

    private static final int MAX_RECENT_RUNS = 80;

    private final LinkedList<AgentRunMetrics> recentRuns = new LinkedList<>();

    private final Map<String, AgentRunMetrics> runIndex = new ConcurrentHashMap<>();

    private volatile Map<String, Object> latestBaseline = Map.of();

    private volatile Map<String, Object> latestRagBenchmark = Map.of();

    private volatile Map<String, Object> latestWebBenchmark = Map.of();

    private volatile Map<String, Object> latestChatBenchmark = Map.of();

    public synchronized void saveRun(AgentRunMetrics metrics) {
        if (metrics == null || metrics.getRequestId() == null) {
            return;
        }
        recentRuns.removeIf(item -> metrics.getRequestId().equals(item.getRequestId()));
        recentRuns.addFirst(metrics);
        runIndex.put(metrics.getRequestId(), metrics);
        while (recentRuns.size() > MAX_RECENT_RUNS) {
            AgentRunMetrics removed = recentRuns.removeLast();
            if (removed != null && removed.getRequestId() != null) {
                runIndex.remove(removed.getRequestId());
            }
        }
    }

    public AgentRunMetrics getRun(String requestId) {
        return requestId == null ? null : runIndex.get(requestId);
    }

    public synchronized List<AgentRunMetrics> listRecentRuns(int limit) {
        int size = limit <= 0 ? 20 : Math.min(limit, recentRuns.size());
        return new ArrayList<>(recentRuns.subList(0, size));
    }

    public Map<String, Object> getLatestBaseline() {
        return latestBaseline;
    }

    public void setLatestBaseline(Map<String, Object> latestBaseline) {
        this.latestBaseline = copyMap(latestBaseline);
    }

    public Map<String, Object> getLatestRagBenchmark() {
        return latestRagBenchmark;
    }

    public void setLatestRagBenchmark(Map<String, Object> latestRagBenchmark) {
        this.latestRagBenchmark = copyMap(latestRagBenchmark);
    }

    public Map<String, Object> getLatestWebBenchmark() {
        return latestWebBenchmark;
    }

    public void setLatestWebBenchmark(Map<String, Object> latestWebBenchmark) {
        this.latestWebBenchmark = copyMap(latestWebBenchmark);
    }

    public Map<String, Object> getLatestChatBenchmark() {
        return latestChatBenchmark;
    }

    public void setLatestChatBenchmark(Map<String, Object> latestChatBenchmark) {
        this.latestChatBenchmark = copyMap(latestChatBenchmark);
    }

    private Map<String, Object> copyMap(Map<String, Object> source) {
        return source == null ? Map.of() : new LinkedHashMap<>(source);
    }
}
