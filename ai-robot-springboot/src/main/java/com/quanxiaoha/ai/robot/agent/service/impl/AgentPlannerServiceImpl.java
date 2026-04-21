package com.quanxiaoha.ai.robot.agent.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanxiaoha.ai.robot.agent.model.AgentContext;
import com.quanxiaoha.ai.robot.agent.model.AgentIntent;
import com.quanxiaoha.ai.robot.agent.model.PlannerDecision;
import com.quanxiaoha.ai.robot.agent.service.AgentPlannerService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 受控 Planner：优先尝试模型输出结构化决策，失败时退回规则兜底。
 */
@Service
@Slf4j
public class AgentPlannerServiceImpl implements AgentPlannerService {

    private static final String PLANNER_SYSTEM_PROMPT = """
            你是一个受控的 Agent Planner，只输出 JSON，不要输出 markdown、解释或代码块。
            请根据用户问题判断：
            1. intent: GENERAL_CHAT / RESUME_OPTIMIZE / INTERVIEW_HELP / CAREER_ADVICE
            2. needKnowledgeSearch: 是否需要查询内部知识库
            3. needWebSearch: 是否需要查询联网信息
            4. useSearchTools: 是否建议开启工具调用
            5. toolQuery: 若需要搜索，给出更适合检索的查询语句
            6. responseStyle: 简短描述回答风格
            7. reason: 简短说明原因
            仅返回一个 JSON 对象。
            """;

    @Resource
    private ChatClient chatClient;
    @Resource
    private ObjectMapper objectMapper;

    @Override
    public PlannerDecision planChat(AgentContext context) {
        PlannerDecision fallback = heuristicDecision(context);
        try {
            String userPrompt = """
                    用户问题：
                    %s

                    当前开关：
                    - knowledgeRag=%s
                    - networkSearch=%s
                    - searchToolEnabled=%s
                    """.formatted(
                    StringUtils.defaultString(context.getUserInput()),
                    context.isKnowledgeRag(),
                    context.isNetworkSearch(),
                    context.isSearchToolEnabled());

            String content = chatClient.prompt()
                    .system(PLANNER_SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();
            if (StringUtils.isBlank(content)) {
                return fallback;
            }
            return parseDecision(content, fallback);
        } catch (Exception e) {
            log.warn("Planner 模型决策失败，退回规则兜底：{}", e.getMessage());
            return fallback;
        }
    }

    private PlannerDecision parseDecision(String content, PlannerDecision fallback) {
        try {
            String json = stripMarkdownCodeFence(content);
            JsonNode root = objectMapper.readTree(json);
            AgentIntent intent = parseIntent(root.path("intent").asText(null), fallback.getIntent());
            return PlannerDecision.builder()
                    .intent(intent)
                    .needKnowledgeSearch(root.path("needKnowledgeSearch").asBoolean(fallback.isNeedKnowledgeSearch()))
                    .needWebSearch(root.path("needWebSearch").asBoolean(fallback.isNeedWebSearch()))
                    .useSearchTools(root.path("useSearchTools").asBoolean(fallback.isUseSearchTools()))
                    .toolQuery(defaultIfBlank(root.path("toolQuery").asText(null), fallback.getToolQuery()))
                    .responseStyle(defaultIfBlank(root.path("responseStyle").asText(null), fallback.getResponseStyle()))
                    .reason(defaultIfBlank(root.path("reason").asText(null), fallback.getReason()))
                    .build();
        } catch (Exception e) {
            log.warn("Planner JSON 解析失败，退回规则兜底：{}", e.getMessage());
            return fallback;
        }
    }

    private PlannerDecision heuristicDecision(AgentContext context) {
        String text = StringUtils.defaultString(context.getUserInput()).toLowerCase();
        boolean needWeb = context.isNetworkSearch()
                || text.contains("最新")
                || text.contains("趋势")
                || text.contains("今年")
                || text.contains("行情")
                || text.contains("新闻");
        boolean needKnowledge = context.isKnowledgeRag()
                || text.contains("简历")
                || text.contains("star")
                || text.contains("项目")
                || text.contains("面试")
                || text.contains("求职");
        AgentIntent intent = fallbackIntent(text, context.getIntent());

        return PlannerDecision.builder()
                .intent(intent)
                .needKnowledgeSearch(needKnowledge)
                .needWebSearch(needWeb)
                .useSearchTools(needKnowledge || needWeb || context.isSearchToolEnabled())
                .toolQuery(StringUtils.defaultIfBlank(context.getUserInput(), context.getTargetPosition()))
                .responseStyle(needWeb ? "优先引用搜索证据并给出结论" : "结构化、可执行、偏实战")
                .reason("规则兜底")
                .build();
    }

    private AgentIntent fallbackIntent(String text, AgentIntent defaultIntent) {
        if (text.contains("面试")) {
            return AgentIntent.INTERVIEW_HELP;
        }
        if (text.contains("职业") || text.contains("发展") || text.contains("求职")) {
            return AgentIntent.CAREER_ADVICE;
        }
        if (text.contains("简历") || text.contains("star") || text.contains("项目")) {
            return AgentIntent.RESUME_OPTIMIZE;
        }
        return defaultIntent == null ? AgentIntent.GENERAL_CHAT : defaultIntent;
    }

    private AgentIntent parseIntent(String raw, AgentIntent fallback) {
        if (StringUtils.isBlank(raw)) {
            return fallback;
        }
        try {
            return AgentIntent.valueOf(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private String stripMarkdownCodeFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstBreak = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstBreak > -1 && lastFence > firstBreak) {
                return trimmed.substring(firstBreak + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private String defaultIfBlank(String candidate, String fallback) {
        return StringUtils.isBlank(candidate) ? fallback : candidate;
    }
}
