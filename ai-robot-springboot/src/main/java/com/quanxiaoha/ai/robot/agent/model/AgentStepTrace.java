package com.quanxiaoha.ai.robot.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 单步执行轨迹。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentStepTrace {

    private String stepName;

    private long durationMs;

    private String summary;
}
