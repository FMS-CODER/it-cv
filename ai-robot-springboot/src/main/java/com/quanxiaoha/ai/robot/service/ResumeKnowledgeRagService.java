package com.quanxiaoha.ai.robot.service;

/**
 * 简历知识库 RAG：双路向量检索（输入语义 + 输出/补充语义），合并去重后供拼入系统提示词。
 */
public interface ResumeKnowledgeRagService {

    /**
     * 智能对话场景：基于用户当前输入做两路检索并格式化为模型可读片段。
     *
     * @param userMessage 用户本轮消息
     * @param category    知识库分类过滤（可为空表示全库）
     * @param topK        合并后最多保留条数（两路各取约一半，再合并去重）
     */
    String buildChatRagContext(String userMessage, String category, int topK);

    /**
     * 简历优化场景：基于岗位 + 简历摘要 + 额外要求做两路检索。
     */
    String buildResumeOptimizeRagContext(String targetPosition, String resumeText, String additionalRequirements,
                                         String category, int topK);
}
