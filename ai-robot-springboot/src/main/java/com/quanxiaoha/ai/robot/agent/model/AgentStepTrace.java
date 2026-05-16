package com.quanxiaoha.ai.robot.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Agent 单步执行记录 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentStepTrace {

    /** 步骤名称 */
    private String stepName;

    /** 耗时（毫秒） */
    private long durationMs;

    /** 步骤摘要 */
    private String summary;
}
