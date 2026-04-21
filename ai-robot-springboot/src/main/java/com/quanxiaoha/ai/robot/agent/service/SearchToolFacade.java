package com.quanxiaoha.ai.robot.agent.service;

/**
 * 搜索工具统一门面。
 */
public interface SearchToolFacade {

    String searchKnowledge(String query, String category, int topK);

    String searchWeb(String query, int topK);
}
