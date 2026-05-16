package com.quanxiaoha.ai.robot.model.vo.knowledge;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 批量导入知识库请求 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportResumeKnowledgeReqVO {

    /** 待导入条目列表 */
    @NotEmpty(message = "导入数据不能为空")
    @Valid
    private List<ResumeKnowledgeItemVO> items;
}
