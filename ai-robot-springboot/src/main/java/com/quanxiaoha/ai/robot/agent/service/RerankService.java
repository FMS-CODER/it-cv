package com.quanxiaoha.ai.robot.agent.service;

import com.quanxiaoha.ai.robot.agent.model.RankedDocument;

import java.util.List;

/** 语义重排服务接口 */
public interface RerankService {

    /**
     * 对候选文档做语义重排
     * @param query     用户原始问题
     * @param documents 候选文档列表
     * @return 按 rerankScore 降序排列，每个 doc 的 rerankScore 已填充
     */
    List<RankedDocument> rerank(String query, List<RankedDocument> documents);
}
