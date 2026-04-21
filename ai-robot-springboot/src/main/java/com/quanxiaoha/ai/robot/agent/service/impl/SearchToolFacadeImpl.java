package com.quanxiaoha.ai.robot.agent.service.impl;

import com.quanxiaoha.ai.robot.agent.service.SearchToolFacade;
import com.quanxiaoha.ai.robot.model.dto.SearchResultDTO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.SearchResumeKnowledgeReqVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.SearchResumeKnowledgeRspVO;
import com.quanxiaoha.ai.robot.service.ResumeKnowledgeBaseService;
import com.quanxiaoha.ai.robot.service.SearchResultContentFetcherService;
import com.quanxiaoha.ai.robot.service.SearXNGService;
import com.quanxiaoha.ai.robot.utils.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 搜索工具统一门面实现。
 */
@Service
@Slf4j
public class SearchToolFacadeImpl implements SearchToolFacade {

    @Resource
    private ResumeKnowledgeBaseService resumeKnowledgeBaseService;
    @Resource
    private SearXNGService searXNGService;
    @Resource
    private SearchResultContentFetcherService searchResultContentFetcherService;

    @Override
    public String searchKnowledge(String query, String category, int topK) {
        if (StringUtils.isBlank(query)) {
            return "知识库检索问题为空。";
        }
        Response<List<SearchResumeKnowledgeRspVO>> response = resumeKnowledgeBaseService.searchSimilar(
                SearchResumeKnowledgeReqVO.builder()
                        .query(query.trim())
                        .category(StringUtils.isBlank(category) ? null : category.trim())
                        .topK(topK <= 0 ? 3 : topK)
                        .build());
        if (response == null || !response.isSuccess() || response.getData() == null || response.getData().isEmpty()) {
            return "知识库暂无匹配结果。";
        }

        StringBuilder sb = new StringBuilder("以下为知识库检索结果：\n\n");
        int i = 1;
        for (SearchResumeKnowledgeRspVO item : response.getData()) {
            sb.append("### 片段 ").append(i++).append("\n")
                    .append("- 分类：").append(StringUtils.defaultString(item.getCategory(), "未分类")).append("\n")
                    .append("- 相似度：").append(String.format("%.3f", item.getSimilarity() == null ? 0D : item.getSimilarity())).append("\n")
                    .append(item.getContent()).append("\n\n");
        }
        return sb.toString().trim();
    }

    @Override
    public String searchWeb(String query, int topK) {
        if (StringUtils.isBlank(query)) {
            return "联网搜索问题为空。";
        }
        List<SearchResultDTO> searchResults = searXNGService.search(query.trim());
        if (searchResults == null || searchResults.isEmpty()) {
            return "联网搜索暂无结果。";
        }

        List<SearchResultDTO> fetched = searchResultContentFetcherService
                .batchFetch(searchResults.stream().limit(Math.max(1, topK)).toList(), 7, TimeUnit.SECONDS)
                .join();
        if (fetched == null || fetched.isEmpty()) {
            return "联网搜索结果抓取失败。";
        }

        StringBuilder sb = new StringBuilder("以下为联网搜索结果：\n\n");
        int i = 1;
        for (SearchResultDTO item : fetched) {
            if (StringUtils.isBlank(item.getContent())) {
                continue;
            }
            sb.append("### 结果 ").append(i++).append("\n")
                    .append("- 链接：").append(StringUtils.defaultString(item.getUrl())).append("\n")
                    .append("- 相关性：").append(item.getScore()).append("\n")
                    .append(item.getContent()).append("\n\n");
        }
        return sb.length() == 0 ? "联网搜索暂无可用正文。" : sb.toString().trim();
    }
}
