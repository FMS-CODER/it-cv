package com.quanxiaoha.ai.robot.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Planner 规划决策结果 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerDecision {

    /** 判定意图 */
    private AgentIntent intent;

    /** 是否需要查知识库 */
    private boolean needKnowledgeSearch;

    /** 是否需要联网搜索 */
    private boolean needWebSearch;

    /** 是否走搜索工具链 */
    private boolean useSearchTools;

    /** 搜索工具使用的查询词 */
    private String toolQuery;

    /** 回复风格说明 */
    private String responseStyle;

    /** 决策理由 */
    private String reason;

    /** 是否首次调用工具（true=直接进入 ReAct，false=先让模型直接回答） */
    private boolean initialToolCall;

    /** 最大轮循次数（偏少，如 1~2） */
    @Builder.Default
    private int maxReactSteps = 1;

    /** 工具策略：parallel / sequential */
    @Builder.Default
    private String toolStrategy = "parallel";
}
