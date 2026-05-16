package com.quanxiaoha.ai.robot.model.vo.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 知识库分页列表项 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FindResumeKnowledgePageListRspVO {

    /** 主键 */
    private Long id;

    /** 知识正文 */
    private String content;

    /** 分类 */
    private String category;

    /** 元数据 JSON */
    private String metadata;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
