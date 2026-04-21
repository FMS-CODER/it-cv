package com.quanxiaoha.ai.robot.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 受控 Planner 的决策结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerDecision {

    private AgentIntent intent;

    private boolean needKnowledgeSearch;

    private boolean needWebSearch;

    private boolean useSearchTools;

    private String toolQuery;

    private String responseStyle;

    private String reason;
}
