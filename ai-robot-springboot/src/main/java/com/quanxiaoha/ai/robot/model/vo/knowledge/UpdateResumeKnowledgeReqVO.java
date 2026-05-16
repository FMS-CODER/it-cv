package com.quanxiaoha.ai.robot.model.vo.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 更新知识库条目请求 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateResumeKnowledgeReqVO {

    /** 条目主键 */
    @NotNull(message = "id 不能为空")
    private Long id;

    /** 知识正文 */
    @NotBlank(message = "content 不能为空")
    private String content;

    /** 分类（可选） */
    private String category;

    /** 元数据 JSON（可选） */
    private JsonNode metadata;
}
