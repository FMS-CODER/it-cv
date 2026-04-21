package com.quanxiaoha.ai.robot.agent.step;

import com.quanxiaoha.ai.robot.agent.model.AgentContext;
import com.quanxiaoha.ai.robot.agent.model.AgentScene;
import com.quanxiaoha.ai.robot.service.ResumeKnowledgeRagService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 组装 RAG 等上下文。
 */
@Component
public class ContextAssemblerStep implements AgentStep {

    @Resource
    private ResumeKnowledgeRagService resumeKnowledgeRagService;

    @Override
    public String getName() {
        return "ContextAssemblerStep";
    }

    @Override
    public void execute(AgentContext context) {
        if (!context.isKnowledgeRag()) {
            context.setRagContext("");
            return;
        }

        String kbCategory = StringUtils.isBlank(context.getKbCategory()) ? null : context.getKbCategory();
        if (context.getScene() == AgentScene.CHAT) {
            context.setRagContext(resumeKnowledgeRagService.buildChatRagContext(
                    context.getUserInput(), kbCategory, context.getKbTopK()));
            return;
        }

        context.setRagContext(resumeKnowledgeRagService.buildResumeOptimizeRagContext(
                context.getTargetPosition(),
                context.getResumeText(),
                context.getAdditionalRequirements(),
                kbCategory,
                context.getKbTopK()));
    }
}
