package com.quanxiaoha.ai.robot.model.vo.knowledge;

import com.quanxiaoha.ai.robot.model.common.BasePageQuery;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: 小明
 * @Date: 2026/4/17
 * @Version: v1.0.0
 * @Description: 知识库分页查询
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FindResumeKnowledgePageListReqVO extends BasePageQuery {

    /**
     * 分类（模糊匹配，可选）
     */
    private String category;
}

