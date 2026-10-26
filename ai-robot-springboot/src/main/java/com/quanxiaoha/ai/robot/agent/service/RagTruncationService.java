package com.quanxiaoha.ai.robot.agent.service;

import com.quanxiaoha.ai.robot.agent.model.RankedDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 相似度截断服务：根据分数阈值过滤低质量片段 */
@Service
public class RagTruncationService {

    /** 软阈值：低于该分数视为「弱相关」，默认不注入 */
    @Value("${rag.truncation.similarity-threshold:0.35}")
    private double similarityThreshold;

    /** 最多注入的片段数 */
    @Value("${rag.truncation.max-top-k:8}")
    private int maxTopK;

    /** 硬下限：低于该分数视为「完全无关」，任何情况下都不注入 */
    @Value("${rag.truncation.hard-floor:0.2}")
    private double hardFloor;

    /** 保底条数：当全部候选都低于软阈值时，至少保留的条数（0=允许为空） */
    @Value("${rag.truncation.min-keep:1}")
    private int minKeep;

    /**
     * 按相似度分数截断，保留质量达标的片段。
     *
     * <p>处理顺序：先按硬下限剔除完全无关片段；再按软阈值筛选达标片段并限制条数；
     * 若结果为空的候选集（全部低于软阈值但仍高于硬下限），则按 {@code min-keep} 保底，
     * 避免知识库上下文「静默为空」导致模型完全裸答、更容易产生幻觉。</p>
     *
     * @param documents 已按分数降序排列的文档
     * @return 截断后的文档列表
     */
    public List<RankedDocument> truncateBySimilarity(List<RankedDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return documents;
        }

        List<RankedDocument> qualified = new ArrayList<>();
        List<RankedDocument> aboveHardFloor = new ArrayList<>();
        for (RankedDocument doc : documents) {
            double score = doc.effectiveScore();
            // 硬下限：完全无关的片段直接丢弃
            if (score < hardFloor) continue;
            aboveHardFloor.add(doc);
            // 软阈值达标且未超上限的片段优先注入
            if (score >= similarityThreshold && qualified.size() < maxTopK) {
                qualified.add(doc);
            }
        }

        // 全部低于软阈值：按保底条数注入分数最高的若干条，避免上下文为空
        if (qualified.isEmpty() && minKeep > 0 && !aboveHardFloor.isEmpty()) {
            int keep = Math.min(minKeep, aboveHardFloor.size());
            return new ArrayList<>(aboveHardFloor.subList(0, keep));
        }

        return qualified;
    }
}
