package com.quanxiaoha.ai.robot.model.vo.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 知识库向量检索单条结果 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResumeKnowledgeRspVO {

    /** 条目主键 */
    private Long id;

    /** 知识正文 */
    private String content;

    /** 分类 */
    private String category;

    /** 元数据 JSON */
    private String metadata;

    /** 相似度，越大越相关，约 [0, 1] */
    private Double similarity;
}
