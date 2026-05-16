package com.quanxiaoha.ai.robot.agent.service;

/**
 * 搜索工具统一门面。
 */
public interface SearchToolFacade {

    String searchKnowledge(String query, String category, int topK);

    /**
     * 知识库检索 → 语义重排 → 相似度截断 → 格式化输出。
     * 相比 searchKnowledge() 多了重排和截断环节，适合工具调用场景。
     */
    String searchKnowledgeWithRerank(String query, String category, int topK);

    String searchWeb(String query, int topK);
}
