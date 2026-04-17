package com.quanxiaoha.ai.robot.domain.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.quanxiaoha.ai.robot.utils.mybatis.JsonbTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @Author: 小明
 * @Date: 2026/4/17
 * @Version: v1.0.0
 * @Description: 简历知识库 DO 实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "resume_knowledge_base", autoResultMap = true)
public class ResumeKnowledgeBaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 知识文本内容
     */
    private String content;

    /**
     * JSONB 元数据（此处以字符串形式保存）
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String metadata;

    /**
     * 向量 embedding
     * - 当启用 pgvector 时，列类型为 vector(n)，可直接接受形如 "[0.1,0.2,...]" 的文本输入
     * - 当未启用 pgvector 时，列类型可能为 TEXT，此时同样存上述文本，便于后续迁移
     */
    private String embedding;

    /**
     * 分类
     */
    private String category;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

