package com.quanxiaoha.ai.robot.metrics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 压测统计摘要。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenchmarkStats {

    private int sampleCount;

    private int successCount;

    private double successRate;

    private long minMs;

    private long avgMs;

    private long p50Ms;

    private long p95Ms;

    private long p99Ms;

    private long maxMs;

    private Double avgScore;

    public static BenchmarkStats fromDurations(List<Long> durations,
                                               int sampleCount,
                                               int successCount,
                                               List<Double> scores) {
        List<Long> safeDurations = durations == null ? List.of() : durations.stream().filter(v -> v != null && v >= 0).toList();
        List<Double> safeScores = scores == null ? List.of() : scores.stream().filter(v -> v != null && !Double.isNaN(v)).toList();
        return BenchmarkStats.builder()
                .sampleCount(sampleCount)
                .successCount(successCount)
                .successRate(sampleCount <= 0 ? 0D : round(successCount * 100D / sampleCount))
                .minMs(safeDurations.isEmpty() ? 0L : Collections.min(safeDurations))
                .avgMs(safeDurations.isEmpty() ? 0L : Math.round(safeDurations.stream().mapToLong(Long::longValue).average().orElse(0D)))
                .p50Ms(percentile(safeDurations, 0.50D))
                .p95Ms(percentile(safeDurations, 0.95D))
                .p99Ms(percentile(safeDurations, 0.99D))
                .maxMs(safeDurations.isEmpty() ? 0L : Collections.max(safeDurations))
                .avgScore(safeScores.isEmpty() ? null : round(safeScores.stream().mapToDouble(Double::doubleValue).average().orElse(0D)))
                .build();
    }

    private static long percentile(List<Long> raw, double percentile) {
        if (raw == null || raw.isEmpty()) {
            return 0L;
        }
        List<Long> sorted = new ArrayList<>(raw);
        sorted.sort(Long::compareTo);
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    private static double round(double value) {
        return Math.round(value * 100D) / 100D;
    }
}
