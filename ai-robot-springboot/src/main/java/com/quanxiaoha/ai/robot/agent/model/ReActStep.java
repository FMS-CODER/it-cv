package com.quanxiaoha.ai.robot.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** ReAct 单轮执行记录，支持多工具并行调用 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReActStep {

    private int stepIndex;

    private String thought;

    private String observation;

    @Builder.Default
    private List<ReActToolCall> toolCalls = new ArrayList<>();

    private long durationMs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReActToolCall {

        private String toolName;

        private String toolArgs;

        private String toolResult;
    }
}
