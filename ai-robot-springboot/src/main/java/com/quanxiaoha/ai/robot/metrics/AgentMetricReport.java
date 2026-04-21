package com.quanxiaoha.ai.robot.metrics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 指标报告与简历输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMetricReport {

    private String generatedAt;

    private String markdown;

    @Builder.Default
    private List<String> resumeBullets = new ArrayList<>();
}
