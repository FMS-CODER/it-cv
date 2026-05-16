package com.quanxiaoha.ai.robot.model.vo.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 知识库导入/写入单条 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeKnowledgeItemVO {

    /** 知识正文 */
    @NotBlank(message = "content 不能为空")
    private String content;

    /** 分类（可选） */
    private String category;

    /** 元数据 JSON 对象或字符串（可选） */
    private JsonNode metadata;
}
