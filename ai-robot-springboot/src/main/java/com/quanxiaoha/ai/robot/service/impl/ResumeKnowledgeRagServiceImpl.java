package com.quanxiaoha.ai.robot.service.impl;

import com.quanxiaoha.ai.robot.agent.model.RankedDocument;
import com.quanxiaoha.ai.robot.agent.service.RagTruncationService;
import com.quanxiaoha.ai.robot.agent.service.RerankService;
import com.quanxiaoha.ai.robot.model.vo.knowledge.SearchResumeKnowledgeReqVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.SearchResumeKnowledgeRspVO;
import com.quanxiaoha.ai.robot.service.ResumeKnowledgeBaseService;
import com.quanxiaoha.ai.robot.service.ResumeKnowledgeRagService;
import com.quanxiaoha.ai.robot.utils.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 双路 RAG：一路偏「输入语义」，一路偏「回答/优化方向」。
 * 检索 → 重排 → 相似度截断 → 格式化注入。
 */
@Service
@Slf4j
public class ResumeKnowledgeRagServiceImpl implements ResumeKnowledgeRagService {

    private static final String OUTPUT_ANCHOR_CHAT =
            "\n\n（请从简历撰写、面试表达、职业发展、量化成果等角度补充相关条目）";

    private static final String OUTPUT_ANCHOR_RESUME =
            "\n\n（请从简历结构、STAR、项目描述、技能关键词、面试话术等角度补充相关条目）";

    @Value("${rag.retrieval.expand-factor:2}")
    private int expandFactor;

    @Value("${rag.rerank.enabled:true}")
    private boolean rerankEnabled;

    @Resource
    private ResumeKnowledgeBaseService resumeKnowledgeBaseService;

    @Resource
    private RerankService rerankService;

    @Resource
    private RagTruncationService ragTruncationService;

    @Override
    public String buildChatRagContext(String userMessage, String category, int topK) {
        if (StringUtils.isBlank(userMessage)) {
            return "";
        }
        int k = topK <= 0 ? 5 : topK;

        String q1 = userMessage.trim();
        String q2 = userMessage.trim() + OUTPUT_ANCHOR_CHAT;

        List<RankedDocument> finalDocs = retrieveRerankTruncate(q1, q2, category, k);
        return formatContext("对话", finalDocs);
    }

    @Override
    public String buildResumeOptimizeRagContext(String targetPosition, String resumeText, String additionalRequirements,
                                                String category, int topK) {
        String pos = StringUtils.defaultString(targetPosition).trim();
        String resume = truncate(StringUtils.defaultString(resumeText), 2000);
        String extra = StringUtils.defaultString(additionalRequirements).trim();

        int k = topK <= 0 ? 5 : topK;

        String q1 = pos + "\n\n" + resume;
        String q2 = pos + (extra.isEmpty() ? "" : "\n额外要求：" + extra) + OUTPUT_ANCHOR_RESUME;

        List<RankedDocument> finalDocs = retrieveRerankTruncate(q1, q2, category, k);
        return formatContext("简历优化", finalDocs);
    }

    private List<RankedDocument> retrieveRerankTruncate(String query1, String query2, String category, int topK) {
        int expandK = topK * expandFactor;
        int perQuery = Math.max(2, (expandK + 1) / 2);

        List<RankedDocument> candidates = mergeDualSearch(query1, query2, category, perQuery);
        if (candidates.isEmpty()) return candidates;

        if (rerankEnabled) {
            candidates = rerankService.rerank(query1, candidates);
        } else {
            candidates.sort(Comparator.comparingDouble(RankedDocument::effectiveScore).reversed());
        }

        return ragTruncationService.truncateBySimilarity(candidates);
    }

    private List<RankedDocument> mergeDualSearch(String query1, String query2, String category, int perQuery) {
        List<SearchResumeKnowledgeRspVO> a = searchOne(query1, category, perQuery);
        List<SearchResumeKnowledgeRspVO> b = searchOne(query2, category, perQuery);

        Map<Long, RankedDocument> byId = new LinkedHashMap<>();
        for (SearchResumeKnowledgeRspVO r : a) {
            if (r != null && r.getId() != null) {
                byId.putIfAbsent(r.getId(), RankedDocument.fromSearchVO(r));
            }
        }
        for (SearchResumeKnowledgeRspVO r : b) {
            if (r != null && r.getId() != null && !byId.containsKey(r.getId())) {
                byId.put(r.getId(), RankedDocument.fromSearchVO(r));
            }
        }
        return new ArrayList<>(byId.values());
    }

    private List<SearchResumeKnowledgeRspVO> searchOne(String query, String category, int topK) {
        SearchResumeKnowledgeReqVO req = SearchResumeKnowledgeReqVO.builder()
                .query(query)
                .topK(topK)
                .category(StringUtils.isNotBlank(category) ? category.trim() : null)
                .build();
        Response<List<SearchResumeKnowledgeRspVO>> resp = resumeKnowledgeBaseService.searchSimilar(req);
        if (resp == null || !resp.isSuccess() || resp.getData() == null) {
            if (resp != null && StringUtils.isNotBlank(resp.getMessage())) {
                log.warn("知识库 RAG 检索未成功: {}", resp.getMessage());
            }
            return List.of();
        }
        return resp.getData();
    }

    private String formatContext(String scene, List<RankedDocument> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## 【知识库 RAG · ").append(scene).append("】\n");
        sb.append("以下为从向量知识库检索到的参考片段（按相关性排序）。请优先据此组织回答，必要时再补充通用经验；勿虚构知识库中不存在的事实。\n\n");
        int i = 1;
        for (RankedDocument r : items) {
            if (r == null || StringUtils.isBlank(r.getContent())) {
                continue;
            }
            double sim = r.effectiveScore();
            String cat = StringUtils.defaultString(r.getCategory());
            sb.append("### 片段 ").append(i++).append("（相关性 ").append(String.format("%.3f", sim)).append("，分类：")
                    .append(cat).append("）\n");
            sb.append(r.getContent().trim()).append("\n\n");
        }
        return sb.toString().trim();
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "\n…（已截断）";
    }
}
