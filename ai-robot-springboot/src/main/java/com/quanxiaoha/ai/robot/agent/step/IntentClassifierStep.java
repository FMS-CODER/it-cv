package com.quanxiaoha.ai.robot.agent.step;

import com.quanxiaoha.ai.robot.agent.model.AgentContext;
import com.quanxiaoha.ai.robot.agent.model.AgentIntent;
import com.quanxiaoha.ai.robot.agent.model.AgentScene;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 固定多步中的意图分类步骤。
 */
@Component
public class IntentClassifierStep implements AgentStep {

    @Override
    public String getName() {
        return "IntentClassifierStep";
    }

    @Override
    public void execute(AgentContext context) {
        if (context.getScene() == AgentScene.RESUME_OPTIMIZE) {
            context.setIntent(AgentIntent.RESUME_OPTIMIZE);
            return;
        }

        String text = StringUtils.defaultString(context.getUserInput()).toLowerCase();
        if (text.contains("面试") || text.contains("interview")) {
            context.setIntent(AgentIntent.INTERVIEW_HELP);
            return;
        }
        if (text.contains("职业") || text.contains("发展") || text.contains("求职") || text.contains("offer")) {
            context.setIntent(AgentIntent.CAREER_ADVICE);
            return;
        }
        if (text.contains("简历") || text.contains("star") || text.contains("项目") || text.contains("岗位")) {
            context.setIntent(AgentIntent.RESUME_OPTIMIZE);
            return;
        }
        context.setIntent(AgentIntent.GENERAL_CHAT);
    }
}
