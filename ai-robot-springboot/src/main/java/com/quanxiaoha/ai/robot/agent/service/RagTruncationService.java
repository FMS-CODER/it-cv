package com.quanxiaoha.ai.robot.agent.service;

import com.quanxiaoha.ai.robot.agent.model.RankedDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 相似度截断服务：根据分数阈值过滤低质量片段 */
@Service
public class RagTruncationService {

    @Value("${rag.truncation.similarity-threshold:0.35}")
    private double similarityThreshold;

    @Value("${rag.truncation.max-top-k:8}")
    private int maxTopK;

    /**
     * 按相似度分数截断，保留质量达标的片段
     * @param documents 已按分数降序排列的文档
     * @return 截断后的文档列表
     */
    public List<RankedDocument> truncateBySimilarity(List<RankedDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return documents;
        }

        List<RankedDocument> result = new ArrayList<>();
        for (RankedDocument doc : documents) {
            if (result.size() >= maxTopK) break;

            double score = doc.effectiveScore();
            if (score < similarityThreshold) continue;

            result.add(doc);
        }

        return result;
    }
}
