package com.quanxiaoha.ai.robot.service;

import com.quanxiaoha.ai.robot.model.vo.knowledge.FindResumeKnowledgePageListReqVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.FindResumeKnowledgePageListRspVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.ImportResumeKnowledgeReqVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.ImportResumeKnowledgeRspVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.SearchResumeKnowledgeReqVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.SearchResumeKnowledgeRspVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.UpdateResumeKnowledgeReqVO;
import com.quanxiaoha.ai.robot.utils.PageResponse;
import com.quanxiaoha.ai.robot.utils.Response;

import java.util.List;

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
     * 更新单条知识并同步重算 embedding
     */
    Response<Boolean> updateKnowledge(UpdateResumeKnowledgeReqVO reqVO);

    /**
     * 分页查询
     */
    PageResponse<FindResumeKnowledgePageListRspVO> pageList(FindResumeKnowledgePageListReqVO reqVO);

    /**
     * 回填历史数据的 embedding（扫描 embedding 为空的记录，批量调用 DashScope 向量化并写回）
     *
     * @param batchSize 每批处理的条数（为空时默认 16）
     * @return 成功回填的条数
     */
    Response<Integer> refillEmbeddings(Integer batchSize);

    /**
     * 基于 DashScope 向量 + pgvector 余弦距离的 Top-K 相似度检索
     */
    Response<List<SearchResumeKnowledgeRspVO>> searchSimilar(SearchResumeKnowledgeReqVO reqVO);
}
