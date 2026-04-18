package com.quanxiaoha.ai.robot.service.impl;

import com.quanxiaoha.ai.robot.model.vo.knowledge.SearchResumeKnowledgeReqVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.SearchResumeKnowledgeRspVO;
import com.quanxiaoha.ai.robot.service.ResumeKnowledgeBaseService;
import com.quanxiaoha.ai.robot.service.ResumeKnowledgeRagService;
import com.quanxiaoha.ai.robot.utils.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 双路 RAG：一路偏「输入语义」，一路偏「回答/优化方向」，合并去重后写入系统提示。
 */
@Service
@Slf4j
public class ResumeKnowledgeRagServiceImpl implements ResumeKnowledgeRagService {

    private static final String OUTPUT_ANCHOR_CHAT =
            "\n\n（请从简历撰写、面试表达、职业发展、量化成果等角度补充相关条目）";

    private static final String OUTPUT_ANCHOR_RESUME =
            "\n\n（请从简历结构、STAR、项目描述、技能关键词、面试话术等角度补充相关条目）";

    @Resource
    private ResumeKnowledgeBaseService resumeKnowledgeBaseService;

    @Override
    public String buildChatRagContext(String userMessage, String category, int topK) {
        if (StringUtils.isBlank(userMessage)) {
            return "";
        }
        int k = topK <= 0 ? 5 : topK;
        int perQuery = Math.max(2, (k + 1) / 2);

        String q1 = userMessage.trim();
        String q2 = userMessage.trim() + OUTPUT_ANCHOR_CHAT;

        List<SearchResumeKnowledgeRspVO> merged = mergeDualSearch(q1, q2, category, perQuery, k);
        return formatContext("对话", merged);
    }

    @Override
    public String buildResumeOptimizeRagContext(String targetPosition, String resumeText, String additionalRequirements,
                                                String category, int topK) {
        String pos = StringUtils.defaultString(targetPosition).trim();
        String resume = truncate(StringUtils.defaultString(resumeText), 2000);
        String extra = StringUtils.defaultString(additionalRequirements).trim();

        int k = topK <= 0 ? 5 : topK;
        int perQuery = Math.max(2, (k + 1) / 2);

        // 输入侧：岗位 + 简历摘要
        String q1 = pos + "\n\n" + resume;
        // 输出侧：岗位 + 额外要求 + 锚点（召回写法/模板类条目）
        String q2 = pos + (extra.isEmpty() ? "" : "\n额外要求：" + extra) + OUTPUT_ANCHOR_RESUME;

        List<SearchResumeKnowledgeRspVO> merged = mergeDualSearch(q1, q2, category, perQuery, k);
        return formatContext("简历优化", merged);
    }

    private List<SearchResumeKnowledgeRspVO> mergeDualSearch(String query1, String query2, String category,
                                                             int perQuery, int maxTotal) {
        List<SearchResumeKnowledgeRspVO> a = searchOne(query1, category, perQuery);
        List<SearchResumeKnowledgeRspVO> b = searchOne(query2, category, perQuery);

        Map<Long, SearchResumeKnowledgeRspVO> byId = new LinkedHashMap<>();
        for (SearchResumeKnowledgeRspVO r : a) {
            if (r != null && r.getId() != null) {
                byId.putIfAbsent(r.getId(), r);
            }
        }
        for (SearchResumeKnowledgeRspVO r : b) {
            if (r != null && r.getId() != null && !byId.containsKey(r.getId())) {
                byId.put(r.getId(), r);
            }
        }
        List<SearchResumeKnowledgeRspVO> out = new ArrayList<>(byId.values());
        if (out.size() > maxTotal) {
            return out.subList(0, maxTotal);
        }
        return out;
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

    private String formatContext(String scene, List<SearchResumeKnowledgeRspVO> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## 【知识库 RAG · ").append(scene).append("】\n");
        sb.append("以下为从向量知识库检索到的参考片段（按相似度排序）。请优先据此组织回答，必要时再补充通用经验；勿虚构知识库中不存在的事实。\n\n");
        int i = 1;
        for (SearchResumeKnowledgeRspVO r : items) {
            if (r == null || StringUtils.isBlank(r.getContent())) {
                continue;
            }
            double sim = r.getSimilarity() == null ? 0D : r.getSimilarity();
            String cat = StringUtils.defaultString(r.getCategory());
            sb.append("### 片段 ").append(i++).append("（相似度约 ").append(String.format("%.3f", sim)).append("，分类：")
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
