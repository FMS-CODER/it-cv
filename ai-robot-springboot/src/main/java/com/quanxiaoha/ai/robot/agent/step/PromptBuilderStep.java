package com.quanxiaoha.ai.robot.agent.step;

import com.quanxiaoha.ai.robot.agent.model.AgentContext;
import com.quanxiaoha.ai.robot.agent.model.AgentIntent;
import com.quanxiaoha.ai.robot.agent.model.AgentScene;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 构建场景化 prompt。
 */
@Component
public class PromptBuilderStep implements AgentStep {

    @Override
    public String getName() {
        return "PromptBuilderStep";
    }

    @Override
    public void execute(AgentContext context) {
        if (context.getScene() == AgentScene.RESUME_OPTIMIZE) {
            buildResumeOptimizePrompt(context);
            return;
        }
        buildChatPrompt(context);
    }

    private void buildChatPrompt(AgentContext context) {
        StringBuilder system = new StringBuilder("""
                你是一位专业的简历优化与职业发展顾问，请根据用户意图提供可执行、条理清晰的建议。
                回答时优先给出适合当前问题的结构化建议，避免空泛表述。
                """);
        appendIntentStyle(system, context.getIntent());
        if (StringUtils.isNotBlank(context.getRagContext())) {
            system.append("\n\n").append(context.getRagContext());
        }
        if (context.isSearchToolEnabled()) {
            system.append("\n\n你可以按需调用检索工具获取知识库或网页搜索结果，再基于结果继续回答。");
        } else if (context.isNetworkSearch()) {
            system.append("\n\n如有联网搜索上下文，请优先综合上下文中的新信息作答。");
        }
        if (context.getPlannerDecision() != null && StringUtils.isNotBlank(context.getPlannerDecision().getResponseStyle())) {
            system.append("\n\n回答风格要求：").append(context.getPlannerDecision().getResponseStyle()).append("。");
        }

        context.setSystemPrompt(system.toString().trim());
        context.setUserPrompt(StringUtils.defaultString(context.getUserInput()).trim());
        context.setFinalPromptPreview(buildPreview(context));
    }

    private void buildResumeOptimizePrompt(AgentContext context) {
        StringBuilder system = new StringBuilder("""
                你是一位专业的简历优化专家，需要结合岗位需求、简历文本和用户的额外要求，给出具体、可直接改写进简历的建议。
                回答请使用 Markdown，优先输出结构清晰的优化清单与示例改写。
                """);
        if (StringUtils.isNotBlank(context.getRagContext())) {
            system.append("\n\n").append(context.getRagContext());
        }

        StringBuilder user = new StringBuilder();
        user.append("目标岗位：").append(StringUtils.defaultString(context.getTargetPosition())).append("\n\n");
        if (StringUtils.isNotBlank(context.getAdditionalRequirements())) {
            user.append("额外要求：").append(context.getAdditionalRequirements().trim()).append("\n\n");
        }
        user.append("简历内容：\n").append(StringUtils.defaultString(context.getResumeText()).trim()).append("\n\n");
        user.append("""
                请从以下几个方面输出优化建议：
                1. 简历结构和格式优化
                2. 工作内容描述优化（使用 STAR 法则）
                3. 技能亮点突出
                4. 项目经验优化
                5. 整体建议
                """);

        context.setSystemPrompt(system.toString().trim());
        context.setUserPrompt(user.toString().trim());
        context.setFinalPromptPreview(buildPreview(context));
    }

    private void appendIntentStyle(StringBuilder system, AgentIntent intent) {
        if (intent == null) {
            return;
        }
        switch (intent) {
            case INTERVIEW_HELP -> system.append("\n\n当前意图偏向面试准备，请优先输出面试思路、追问角度与回答模板。");
            case CAREER_ADVICE -> system.append("\n\n当前意图偏向职业发展，请优先输出阶段目标、行动建议与风险提示。");
            case RESUME_OPTIMIZE -> system.append("\n\n当前意图偏向简历优化，请优先输出可直接修改简历的表述建议与示例。");
            default -> system.append("\n\n当前意图为通用咨询，请在必要时引导用户补充岗位、经历或目标。");
        }
    }

    private String buildPreview(AgentContext context) {
        return ("[System]\n" + StringUtils.defaultString(context.getSystemPrompt())
                + "\n\n[User]\n" + StringUtils.defaultString(context.getUserPrompt())).trim();
    }
}
