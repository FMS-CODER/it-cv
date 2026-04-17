package com.quanxiaoha.ai.robot.model.vo.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: 小明
 * @Date: 2026/4/17
 * @Version: v1.0.0
 * @Description: 知识库条目
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeKnowledgeItemVO {

    @NotBlank(message = "content 不能为空")
    private String content;

    /**
     * 分类（可选）
     */
    private String category;

    /**
     * 元数据（可选）
     * - 既支持直接传 JSON 对象/数组（推荐）
     * - 也兼容传 JSON 字符串（比如命令行里不好转义时）
     */
    private JsonNode metadata;
}

