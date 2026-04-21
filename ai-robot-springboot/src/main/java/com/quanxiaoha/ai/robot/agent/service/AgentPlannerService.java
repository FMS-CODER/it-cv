package com.quanxiaoha.ai.robot.agent.service;

import com.quanxiaoha.ai.robot.agent.model.AgentContext;
import com.quanxiaoha.ai.robot.agent.model.PlannerDecision;

/**
 * 受控 Planner 服务。
 */
public interface AgentPlannerService {

    PlannerDecision planChat(AgentContext context);
}
