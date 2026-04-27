package com.quanxiaoha.ai.robot.metrics;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * BenchmarkStats 统计测试。
 */
public class BenchmarkStatsTests {

    @Test
    void shouldCalculatePercentilesAndAverageScore() {
        BenchmarkStats stats = BenchmarkStats.fromDurations(
                List.of(10L, 20L, 30L, 40L, 50L),
                5,
                4,
                List.of(60D, 80D)
        );

        Assertions.assertEquals(5, stats.getSampleCount());
        Assertions.assertEquals(4, stats.getSuccessCount());
        Assertions.assertEquals(80D, stats.getSuccessRate());
        Assertions.assertEquals(10L, stats.getMinMs());
        Assertions.assertEquals(30L, stats.getAvgMs());
        Assertions.assertEquals(30L, stats.getP50Ms());
        Assertions.assertEquals(50L, stats.getP95Ms());
        Assertions.assertEquals(50L, stats.getP99Ms());
        Assertions.assertEquals(50L, stats.getMaxMs());
        Assertions.assertEquals(70D, stats.getAvgScore());
    }
}
