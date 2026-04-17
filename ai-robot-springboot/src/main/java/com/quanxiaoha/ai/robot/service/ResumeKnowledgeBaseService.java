package com.quanxiaoha.ai.robot.service;

import com.quanxiaoha.ai.robot.model.vo.knowledge.FindResumeKnowledgePageListReqVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.FindResumeKnowledgePageListRspVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.ImportResumeKnowledgeReqVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.ImportResumeKnowledgeRspVO;
import com.quanxiaoha.ai.robot.utils.PageResponse;
import com.quanxiaoha.ai.robot.utils.Response;

/**
 * @Author: 小明
 * @Date: 2026/4/17
 * @Version: v1.0.0
 * @Description: 简历知识库
 */
public interface ResumeKnowledgeBaseService {

    /**
     * 批量导入（自定义数据）
     */
    Response<ImportResumeKnowledgeRspVO> importBatch(ImportResumeKnowledgeReqVO reqVO);

    /**
     * 一键导入典型示例数据
     */
    Response<ImportResumeKnowledgeRspVO> importSamples();

    /**
     * 分页查询
     */
    PageResponse<FindResumeKnowledgePageListRspVO> pageList(FindResumeKnowledgePageListReqVO reqVO);
}

