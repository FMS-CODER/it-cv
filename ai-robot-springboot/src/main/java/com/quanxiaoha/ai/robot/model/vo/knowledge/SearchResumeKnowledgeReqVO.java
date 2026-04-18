package com.quanxiaoha.ai.robot.model.vo.knowledge;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: 小明
 * @Date: 2026/4/17
 * @Version: v1.0.0
 * @Description: 知识库向量检索请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResumeKnowledgeReqVO {

    /**
     * 查询文本（会被 embedding 模型向量化后，用于 pgvector 的相似度检索）
     */
    @NotBlank(message = "query 不能为空")
    private String query;

    /**
     * Top-K（可选，默认 5）
     */
    private Integer topK;

    /**
     * 分类过滤（可选）
     */
    private String category;
}
