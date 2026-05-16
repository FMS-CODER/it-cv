package com.quanxiaoha.ai.robot.agent.model;

import com.quanxiaoha.ai.robot.model.vo.knowledge.SearchResumeKnowledgeRspVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 重排文档模型，含向量分数和重排分数 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankedDocument {

    private Long id;

    private String content;

    private String category;

    private Double vectorScore;

    private Double rerankScore;

    public static RankedDocument fromSearchVO(SearchResumeKnowledgeRspVO vo) {
        return RankedDocument.builder()
                .id(vo.getId())
                .content(vo.getContent())
                .category(vo.getCategory())
                .vectorScore(vo.getSimilarity())
                .build();
    }

    public double effectiveScore() {
        if (rerankScore != null) return rerankScore;
        if (vectorScore != null) return vectorScore;
        return 0D;
    }
}
