package com.quanxiaoha.ai.robot.domain.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.quanxiaoha.ai.robot.domain.dos.ResumeKnowledgeBaseDO;
import org.apache.commons.lang3.StringUtils;

/**
 * @Author: 小明
 * @Date: 2026/4/17
 * @Version: v1.0.0
 * @Description: 简历知识库 Mapper
 */
public interface ResumeKnowledgeBaseMapper extends BaseMapper<ResumeKnowledgeBaseDO> {

    default Page<ResumeKnowledgeBaseDO> selectPageList(Long current, Long size, String category) {
        Page<ResumeKnowledgeBaseDO> page = new Page<>(current, size);

        LambdaQueryWrapper<ResumeKnowledgeBaseDO> wrapper = Wrappers.<ResumeKnowledgeBaseDO>lambdaQuery()
                .like(StringUtils.isNotBlank(category), ResumeKnowledgeBaseDO::getCategory, category)
                .orderByDesc(ResumeKnowledgeBaseDO::getCreatedAt);

        return selectPage(page, wrapper);
    }
}

