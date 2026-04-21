package com.quanxiaoha.ai.robot.agent.step;

import com.quanxiaoha.ai.robot.agent.model.AgentContext;

/**
 * Agent 固定步骤接口。
 */
public interface AgentStep {

    String getName();

    void execute(AgentContext context);
}
