package com.quanxiaoha.ai.robot.domain.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.quanxiaoha.ai.robot.utils.mybatis.JsonbTypeHandler;
import com.quanxiaoha.ai.robot.utils.mybatis.PgVectorTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 简历知识库表 resume_knowledge_base */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "resume_knowledge_base", autoResultMap = true)
public class ResumeKnowledgeBaseDO {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 知识文本 */
    private String content;

    /** JSONB 元数据（JSON 字符串） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String metadata;

    /** 向量 embedding，写入时由 PgVectorTypeHandler 转换 */
    @TableField(typeHandler = PgVectorTypeHandler.class)
    private String embedding;

    /** 分类标签 */
    private String category;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
