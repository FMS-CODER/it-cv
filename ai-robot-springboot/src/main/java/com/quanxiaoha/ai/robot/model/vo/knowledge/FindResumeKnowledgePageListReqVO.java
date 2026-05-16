package com.quanxiaoha.ai.robot.model.vo.knowledge;

import com.quanxiaoha.ai.robot.model.common.BasePageQuery;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 知识库分页查询请求 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FindResumeKnowledgePageListReqVO extends BasePageQuery {

    /** 分类模糊匹配（可选） */
    private String category;
}
