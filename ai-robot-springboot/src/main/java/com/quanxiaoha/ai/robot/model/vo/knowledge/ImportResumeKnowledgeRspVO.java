package com.quanxiaoha.ai.robot.model.vo.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 批量导入知识库响应 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportResumeKnowledgeRspVO {

    /** 成功写入条数 */
    private Integer imported;
}
