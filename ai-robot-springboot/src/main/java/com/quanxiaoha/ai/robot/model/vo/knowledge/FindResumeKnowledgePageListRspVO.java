package com.quanxiaoha.ai.robot.model.vo.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @Author: 小明
 * @Date: 2026/4/17
 * @Version: v1.0.0
 * @Description: 知识库分页响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FindResumeKnowledgePageListRspVO {

    private Long id;
    private String content;
    private String category;
    private String metadata;
    private LocalDateTime createdAt;
}

