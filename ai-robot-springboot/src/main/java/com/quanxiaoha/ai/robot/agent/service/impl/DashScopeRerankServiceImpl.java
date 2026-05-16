package com.quanxiaoha.ai.robot.agent.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanxiaoha.ai.robot.agent.model.RankedDocument;
import com.quanxiaoha.ai.robot.agent.service.RerankService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** DashScope GTE-ReRank 重排实现 */
@Service
@Slf4j
public class DashScopeRerankServiceImpl implements RerankService {

    private static final String RERANK_URL = "https://dashscope.aliyuncs.com/api/v1/services/rerank";

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    @Value("${rag.rerank.model:gte-rerank}")
    private String modelName;

    @Resource
    private OkHttpClient okHttpClient;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public List<RankedDocument> rerank(String query, List<RankedDocument> documents) {
        if (documents == null || documents.isEmpty() || StringUtils.isBlank(query)) {
            return documents;
        }

        try {
            List<String> texts = documents.stream()
                    .map(RankedDocument::getContent)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toList());

            if (texts.isEmpty()) return documents;

            String requestBody = buildRequestBody(query, texts);
            Request request = new Request.Builder()
                    .url(RERANK_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("DashScope rerank 调用失败: status={}", response.code());
                    return fallbackToVectorSort(documents);
                }
                String respBody = response.body().string();
                return parseRerankResponse(respBody, documents);
            }
        } catch (Exception e) {
            log.warn("DashScope rerank 异常，退回向量排序: {}", e.getMessage());
            return fallbackToVectorSort(documents);
        }
    }

    private String buildRequestBody(String query, List<String> texts) throws Exception {
        StringBuilder json = new StringBuilder();
        json.append("{\"model\":\"").append(modelName).append("\",");
        json.append("\"input\":{");
        json.append("\"query\":\"").append(escapeJson(query)).append("\",");
        json.append("\"documents\":[");
        for (int i = 0; i < texts.size(); i++) {
            if (i > 0) json.append(",");
            json.append("\"").append(escapeJson(texts.get(i))).append("\"");
        }
        json.append("]");
        json.append("},\"parameters\":{\"top_n\":").append(texts.size()).append("}}");
        return json.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private List<RankedDocument> parseRerankResponse(String respBody, List<RankedDocument> documents) {
        try {
            JsonNode root = objectMapper.readTree(respBody);
            JsonNode results = root.path("output").path("results");
            if (results == null || !results.isArray()) {
                return fallbackToVectorSort(documents);
            }

            List<RankedDocument> reranked = new ArrayList<>();
            for (JsonNode node : results) {
                int index = node.path("index").asInt(-1);
                double score = node.path("score").asDouble(0.0);
                if (index >= 0 && index < documents.size()) {
                    RankedDocument doc = documents.get(index);
                    doc.setRerankScore(score);
                    reranked.add(doc);
                }
            }

            reranked.sort(Comparator.comparingDouble(RankedDocument::effectiveScore).reversed());
            return reranked;
        } catch (Exception e) {
            log.warn("解析 rerank 响应失败: {}", e.getMessage());
            return fallbackToVectorSort(documents);
        }
    }

    private List<RankedDocument> fallbackToVectorSort(List<RankedDocument> documents) {
        documents.sort(Comparator.comparingDouble(RankedDocument::effectiveScore).reversed());
        return documents;
    }
}
