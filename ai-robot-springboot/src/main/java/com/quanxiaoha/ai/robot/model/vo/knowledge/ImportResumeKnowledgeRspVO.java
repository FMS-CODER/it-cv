package com.quanxiaoha.ai.robot.model.vo.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: 小明
 * @Date: 2026/4/17
 * @Version: v1.0.0
 * @Description: 导入简历知识库响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportResumeKnowledgeRspVO {
    /**
     * 成功导入条数
     */
    private Integer imported;
}

