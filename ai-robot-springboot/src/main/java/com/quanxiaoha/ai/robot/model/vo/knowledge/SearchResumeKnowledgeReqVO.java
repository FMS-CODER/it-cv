package com.quanxiaoha.ai.robot.model.vo.knowledge;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 知识库向量检索请求 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResumeKnowledgeReqVO {

    /** 检索文本（向量化后与库内 embedding 比对） */
    @NotBlank(message = "query 不能为空")
    private String query;

    /** 返回条数 Top-K，默认 5 */
    private Integer topK;

    /** 分类过滤（可选） */
    private String category;
}
