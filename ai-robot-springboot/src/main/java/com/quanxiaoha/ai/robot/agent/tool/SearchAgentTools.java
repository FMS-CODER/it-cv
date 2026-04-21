package com.quanxiaoha.ai.robot.agent.tool;

import com.quanxiaoha.ai.robot.agent.service.SearchToolFacade;
import jakarta.annotation.Resource;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 暴露给模型调用的搜索工具。
 */
@Component
public class SearchAgentTools {

    @Resource
    private SearchToolFacade searchToolFacade;

    @Tool(name = "knowledge_search", description = "检索简历知识库，适合查询简历写法、STAR、项目表达、面试话术等。")
    public String knowledgeSearch(
            @ToolParam(description = "知识库检索问题，例如：Java后端简历项目描述怎么写") String query,
            @ToolParam(required = false, description = "知识库分类，可为空，例如：简历通用、面试、项目描述") String category,
            @ToolParam(required = false, description = "返回条数，建议 1 到 5") Integer topK) {
        return searchToolFacade.searchKnowledge(query, category, topK == null ? 3 : topK);
    }

    @Tool(name = "web_search", description = "联网搜索最新网页信息，适合查询行业趋势、最新要求、外部资料。")
    public String webSearch(
            @ToolParam(description = "联网搜索问题，例如：2026 Java后端面试趋势") String query,
            @ToolParam(required = false, description = "返回条数，建议 1 到 5") Integer topK) {
        return searchToolFacade.searchWeb(query, topK == null ? 3 : topK);
    }
}
