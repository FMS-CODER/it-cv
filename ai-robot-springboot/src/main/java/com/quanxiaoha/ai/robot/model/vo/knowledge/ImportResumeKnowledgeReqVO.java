package com.quanxiaoha.ai.robot.model.vo.knowledge;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author: 小明
 * @Date: 2026/4/17
 * @Version: v1.0.0
 * @Description: 导入简历知识库请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportResumeKnowledgeReqVO {

    /**
     * 批量导入条目
     */
    @NotEmpty(message = "导入数据不能为空")
    @Valid
    private List<ResumeKnowledgeItemVO> items;
}

