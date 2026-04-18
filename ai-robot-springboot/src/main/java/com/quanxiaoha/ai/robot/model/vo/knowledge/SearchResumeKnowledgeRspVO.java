package com.quanxiaoha.ai.robot.model.vo.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: 小明
 * @Date: 2026/4/17
 * @Version: v1.0.0
 * @Description: 知识库向量检索结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResumeKnowledgeRspVO {

    private Long id;
    private String content;
    private String category;
    private String metadata;

    /**
     * 相似度分值（越大越相似，范围约 [0, 1]；由 1 - 余弦距离近似得到）
     */
    private Double similarity;
}
