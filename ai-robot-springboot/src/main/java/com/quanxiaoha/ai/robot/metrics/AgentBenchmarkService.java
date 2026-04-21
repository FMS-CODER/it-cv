package com.quanxiaoha.ai.robot.metrics;

import com.quanxiaoha.ai.robot.agent.service.AgentOrchestrator;
import com.quanxiaoha.ai.robot.model.dto.SearchResultDTO;
import com.quanxiaoha.ai.robot.model.vo.chat.AIResponse;
import com.quanxiaoha.ai.robot.model.vo.chat.AiChatReqVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.SearchResumeKnowledgeReqVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.SearchResumeKnowledgeRspVO;
import com.quanxiaoha.ai.robot.service.ResumeKnowledgeBaseService;
import com.quanxiaoha.ai.robot.service.ResumeKnowledgeRagService;
import com.quanxiaoha.ai.robot.service.SearchResultContentFetcherService;
import com.quanxiaoha.ai.robot.service.SearXNGService;
import com.quanxiaoha.ai.robot.utils.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Agent 指标验证与报告服务。
 */
@Service
@Slf4j
public class AgentBenchmarkService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private ApplicationContext applicationContext;
    @Resource
    private DataSource dataSource;
    @Resource
    private ResumeKnowledgeBaseService resumeKnowledgeBaseService;
    @Resource
    private ResumeKnowledgeRagService resumeKnowledgeRagService;
    @Resource
    private SearXNGService searXNGService;
    @Resource
    private SearchResultContentFetcherService searchResultContentFetcherService;
    @Resource
    private AgentOrchestrator agentOrchestrator;
    @Resource
    private AgentMetricsService agentMetricsService;
    @Resource
    private OkHttpClient okHttpClient;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;
    @Value("${searxng.url:}")
    private String searxngUrl;

    public Map<String, Object> buildBaseline(int recentLimit) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", TIME_FORMATTER.format(LocalDateTime.now()));
        result.put("coreSteps", List.of(
                "AgentContext 初始化",
                "意图识别",
                "上下文组装（RAG / Web）",
                "Prompt 构建",
                "Planner 决策",
                "Tool Calling 与降级兜底"));
        result.put("supportedScenes", List.of("智能对话", "简历优化"));
        result.put("unifiedOrchestrator", hasBean("agentOrchestratorImpl"));
        result.put("plannerEnabled", hasBean("agentPlannerServiceImpl"));
        result.put("ragEnabled", hasBean("resumeKnowledgeRagServiceImpl"));
        result.put("searxngConfigured", StringUtils.isNotBlank(searxngUrl));
        result.put("chatHistoryEnabled", tableExists("t_chat") && tableExists("t_chat_message"));
        result.put("knowledgeBaseEnabled", tableExists("resume_knowledge_base"));
        result.put("pgvectorReady", extensionExists("vector"));
        result.put("datasourceUrl", datasourceUrl);
        result.put("searxngUrl", searxngUrl);
        result.put("recentRuns", agentMetricsService.listRecentRuns(recentLimit));
        agentMetricsService.setLatestBaseline(result);
        return result;
    }

    public List<AgentRunMetrics> recentRuns(int limit) {
        return agentMetricsService.listRecentRuns(limit);
    }

    public Map<String, Object> runRagBenchmark(Map<String, Object> payload) {
        int topK = intValue(payload.get("topK"), 5);
        List<RagSample> samples = parseRagSamples(payload.get("samples"));
        if (samples.isEmpty()) {
            samples = defaultRagSamples();
        }

        List<Map<String, Object>> singleItems = new ArrayList<>();
        List<Map<String, Object>> dualItems = new ArrayList<>();
        List<Map<String, Object>> filteredItems = new ArrayList<>();
        List<Long> singleDurations = new ArrayList<>();
        List<Long> dualDurations = new ArrayList<>();
        List<Long> filteredDurations = new ArrayList<>();
        List<Double> singleScores = new ArrayList<>();
        List<Double> dualScores = new ArrayList<>();
        List<Double> filteredScores = new ArrayList<>();
        List<Double> dualScoresOnFilteredSamples = new ArrayList<>();
        int singleSuccess = 0;
        int dualSuccess = 0;
        int filteredSuccess = 0;

        for (RagSample sample : samples) {
            long singleStart = System.currentTimeMillis();
            Response<List<SearchResumeKnowledgeRspVO>> singleResp = resumeKnowledgeBaseService.searchSimilar(SearchResumeKnowledgeReqVO.builder()
                    .query(sample.query())
                    .category(null)
                    .topK(topK)
                    .build());
            long singleDuration = System.currentTimeMillis() - singleStart;
            String singleContext = formatKnowledgeContext(singleResp == null ? null : singleResp.getData());
            boolean singleOk = singleResp != null && singleResp.isSuccess() && StringUtils.isNotBlank(singleContext);
            double singleCoverage = keywordCoverage(sample.expectedKeywords(), singleContext);
            singleItems.add(buildRagItem("single", sample, singleDuration, singleOk, singleCoverage, singleContext));
            singleDurations.add(singleDuration);
            singleScores.add(singleCoverage);
            if (singleOk) {
                singleSuccess++;
            }

            long dualStart = System.currentTimeMillis();
            String dualContext = resumeKnowledgeRagService.buildChatRagContext(sample.query(), null, topK);
            long dualDuration = System.currentTimeMillis() - dualStart;
            boolean dualOk = StringUtils.isNotBlank(dualContext);
            double dualCoverage = keywordCoverage(sample.expectedKeywords(), dualContext);
            dualItems.add(buildRagItem("dual", sample, dualDuration, dualOk, dualCoverage, dualContext));
            dualDurations.add(dualDuration);
            dualScores.add(dualCoverage);
            if (dualOk) {
                dualSuccess++;
            }

            if (StringUtils.isNotBlank(sample.category())) {
                dualScoresOnFilteredSamples.add(dualCoverage);
                long filteredStart = System.currentTimeMillis();
                String filteredContext = resumeKnowledgeRagService.buildChatRagContext(sample.query(), sample.category(), topK);
                long filteredDuration = System.currentTimeMillis() - filteredStart;
                boolean filteredOk = StringUtils.isNotBlank(filteredContext);
                double filteredCoverage = keywordCoverage(sample.expectedKeywords(), filteredContext);
                filteredItems.add(buildRagItem("dual-filtered", sample, filteredDuration, filteredOk, filteredCoverage, filteredContext));
                filteredDurations.add(filteredDuration);
                filteredScores.add(filteredCoverage);
                if (filteredOk) {
                    filteredSuccess++;
                }
            }
        }

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", TIME_FORMATTER.format(LocalDateTime.now()));
        result.put("topK", topK);
        result.put("sampleCount", samples.size());
        result.put("singleRoute", buildBenchmarkBlock(singleItems, BenchmarkStats.fromDurations(singleDurations, samples.size(), singleSuccess, singleScores)));
        result.put("dualRoute", buildBenchmarkBlock(dualItems, BenchmarkStats.fromDurations(dualDurations, samples.size(), dualSuccess, dualScores)));
        result.put("filteredRoute", buildBenchmarkBlock(filteredItems,
                BenchmarkStats.fromDurations(filteredDurations, filteredItems.size(), filteredSuccess, filteredScores)));
        result.put("dualVsSingleCoverageLift", round(avg(dualScores) - avg(singleScores)));
        result.put("filteredVsDualCoverageLift", round(avg(filteredScores) - avg(dualScoresOnFilteredSamples)));
        result.put("note", "相关性评分使用预期关键词覆盖率近似衡量，适合做离线样本对比。");
        agentMetricsService.setLatestRagBenchmark(result);
        return result;
    }

    public Map<String, Object> runWebBenchmark(Map<String, Object> payload) {
        int topK = intValue(payload.get("topK"), 3);
        List<WebSample> samples = parseWebSamples(payload.get("samples"));
        if (samples.isEmpty()) {
            samples = defaultWebSamples();
        }

        List<Map<String, Object>> items = new ArrayList<>();
        List<Long> totalDurations = new ArrayList<>();
        List<Long> searchDurations = new ArrayList<>();
        List<Long> fetchDurations = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        List<Double> compressions = new ArrayList<>();
        int successCount = 0;

        for (WebSample sample : samples) {
            long searchStart = System.currentTimeMillis();
            List<SearchResultDTO> searchResults = searXNGService.search(sample.query());
            long searchDuration = System.currentTimeMillis() - searchStart;
            List<SearchResultDTO> limited = searchResults == null ? List.of() : searchResults.stream().limit(Math.max(1, topK)).toList();

            long fetchStart = System.currentTimeMillis();
            List<SearchResultDTO> fetched = limited.isEmpty()
                    ? List.of()
                    : searchResultContentFetcherService.batchFetch(limited, 7, TimeUnit.SECONDS).join();
            long fetchDuration = System.currentTimeMillis() - fetchStart;

            List<String> rawHtmlList = limited.stream()
                    .map(item -> CompletableFuture.supplyAsync(() -> fetchRawHtml(item.getUrl())))
                    .map(CompletableFuture::join)
                    .filter(StringUtils::isNotBlank)
                    .toList();
            String cleanedContext = fetched == null ? "" : fetched.stream()
                    .map(SearchResultDTO::getContent)
                    .filter(StringUtils::isNotBlank)
                    .reduce("", (a, b) -> a + "\n\n" + b)
                    .trim();
            int rawChars = rawHtmlList.stream().mapToInt(String::length).sum();
            int cleanChars = cleanedContext.length();
            int rawTokens = estimateTokens(String.join("\n", rawHtmlList));
            int cleanTokens = estimateTokens(cleanedContext);
            double compressionRate = rawTokens <= 0 ? 0D : round((1D - cleanTokens * 1D / rawTokens) * 100D);
            boolean success = !limited.isEmpty() && StringUtils.isNotBlank(cleanedContext);
            double coverage = keywordCoverage(sample.expectedKeywords(), cleanedContext);
            long totalDuration = searchDuration + fetchDuration;
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("name", sample.name());
            item.put("query", sample.query());
            item.put("searchDurationMs", searchDuration);
            item.put("fetchDurationMs", fetchDuration);
            item.put("totalDurationMs", totalDuration);
            item.put("resultCount", limited.size());
            item.put("rawChars", rawChars);
            item.put("cleanChars", cleanChars);
            item.put("rawEstimatedTokens", rawTokens);
            item.put("cleanEstimatedTokens", cleanTokens);
            item.put("estimatedTokenCompressionRate", compressionRate);
            item.put("keywordCoverage", coverage);
            item.put("success", success);
            item.put("contextPreview", preview(cleanedContext));
            items.add(item);
            totalDurations.add(totalDuration);
            searchDurations.add(searchDuration);
            fetchDurations.add(fetchDuration);
            scores.add(coverage);
            compressions.add(compressionRate);
            if (success) {
                successCount++;
            }
        }

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", TIME_FORMATTER.format(LocalDateTime.now()));
        result.put("topK", topK);
        result.put("sampleCount", samples.size());
        result.put("totalStats", buildStatsWithExtra(totalDurations, samples.size(), successCount, scores, compressions));
        result.put("searchStats", BenchmarkStats.fromDurations(searchDurations, samples.size(), successCount, null));
        result.put("fetchStats", BenchmarkStats.fromDurations(fetchDurations, samples.size(), successCount, null));
        result.put("items", items);
        result.put("note", "Token 为近似估算值，用于对比清洗前后的上下文压缩效果。");
        agentMetricsService.setLatestWebBenchmark(result);
        return result;
    }

    public Map<String, Object> runChatBenchmark(Map<String, Object> payload) {
        String modelName = stringValue(payload.get("modelName"), "deepseek-reasoner");
        double temperature = doubleValue(payload.get("temperature"), 0.8D);
        List<ChatSample> samples = parseChatSamples(payload.get("samples"), modelName, temperature);
        if (samples.isEmpty()) {
            samples = defaultChatSamples(modelName, temperature);
        }

        List<Map<String, Object>> items = new ArrayList<>();
        List<Long> totalDurations = new ArrayList<>();
        List<Long> firstTokenDurations = new ArrayList<>();
        int successCount = 0;
        int fallbackTriggeredCount = 0;
        int fallbackSuccessCount = 0;

        for (ChatSample sample : samples) {
            String traceId = UUID.randomUUID().toString();
            AiChatReqVO reqVO = AiChatReqVO.builder()
                    .message(sample.message())
                    .chatId(null)
                    .traceId(traceId)
                    .knowledgeRag(sample.knowledgeRag())
                    .networkSearch(sample.networkSearch())
                    .searchToolEnabled(sample.searchToolEnabled())
                    .agentPlanner(sample.agentPlanner())
                    .maxAgentSteps(sample.maxAgentSteps())
                    .modelName(sample.modelName())
                    .temperature(sample.temperature())
                    .build();
            String errorMessage = null;
            try {
                List<AIResponse> ignored = agentOrchestrator.streamChat(reqVO)
                        .collectList()
                        .block(Duration.ofMinutes(3));
                if (ignored == null) {
                    log.warn("聊天压测返回空响应：{}", sample.name());
                }
            } catch (Exception e) {
                errorMessage = e.getMessage();
            }
            AgentRunMetrics metrics = waitForRun(traceId);
            boolean success = metrics != null && metrics.isSuccess();
            long firstToken = metrics == null ? 0L : metrics.getFirstTokenLatencyMs();
            long totalDuration = metrics == null ? 0L : metrics.getTotalDurationMs();
            boolean fallbackTriggered = metrics != null && metrics.isFallbackTriggered();
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("name", sample.name());
            item.put("message", sample.message());
            item.put("modelName", sample.modelName());
            item.put("knowledgeRag", sample.knowledgeRag());
            item.put("networkSearch", sample.networkSearch());
            item.put("searchToolEnabled", sample.searchToolEnabled());
            item.put("agentPlanner", sample.agentPlanner());
            item.put("maxAgentSteps", sample.maxAgentSteps());
            item.put("firstTokenLatencyMs", firstToken);
            item.put("totalDurationMs", totalDuration);
            item.put("outputChars", metrics == null ? 0 : metrics.getOutputChars());
            item.put("reasoningChars", metrics == null ? 0 : metrics.getReasoningChars());
            item.put("toolMode", StringUtils.defaultIfBlank(metrics == null ? null : metrics.getToolMode(), "unknown"));
            item.put("fallbackTriggered", fallbackTriggered);
            item.put("success", success);
            item.put("errorMessage", StringUtils.defaultString(metrics == null ? errorMessage : metrics.getErrorMessage()));
            items.add(item);
            totalDurations.add(totalDuration);
            if (firstToken > 0) {
                firstTokenDurations.add(firstToken);
            }
            if (success) {
                successCount++;
            }
            if (fallbackTriggered) {
                fallbackTriggeredCount++;
                if (success) {
                    fallbackSuccessCount++;
                }
            }
        }

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", TIME_FORMATTER.format(LocalDateTime.now()));
        result.put("modelName", modelName);
        result.put("sampleCount", samples.size());
        result.put("responseStats", BenchmarkStats.fromDurations(totalDurations, samples.size(), successCount, null));
        result.put("firstTokenStats", BenchmarkStats.fromDurations(firstTokenDurations, firstTokenDurations.size(), firstTokenDurations.size(), null));
        result.put("fallbackTriggeredCount", fallbackTriggeredCount);
        result.put("fallbackSuccessRate", fallbackTriggeredCount == 0 ? 0D : round(fallbackSuccessCount * 100D / fallbackTriggeredCount));
        result.put("items", items);
        agentMetricsService.setLatestChatBenchmark(result);
        return result;
    }

    public AgentMetricReport generateReport() {
        Map<String, Object> baseline = agentMetricsService.getLatestBaseline();
        Map<String, Object> rag = agentMetricsService.getLatestRagBenchmark();
        Map<String, Object> web = agentMetricsService.getLatestWebBenchmark();
        Map<String, Object> chat = agentMetricsService.getLatestChatBenchmark();
        List<AgentRunMetrics> recentRuns = agentMetricsService.listRecentRuns(10);

        StringBuilder md = new StringBuilder();
        md.append("## 指标验证报告\n\n");
        md.append("- 生成时间：").append(TIME_FORMATTER.format(LocalDateTime.now())).append("\n");
        if (!baseline.isEmpty()) {
            md.append("- 基线能力：统一编排=").append(boolText(baseline.get("unifiedOrchestrator")))
                    .append("，RAG=").append(boolText(baseline.get("ragEnabled")))
                    .append("，SearXNG=").append(boolText(baseline.get("searxngConfigured")))
                    .append("，pgvector=").append(boolText(baseline.get("pgvectorReady")))
                    .append("\n");
        }

        if (!rag.isEmpty()) {
            Map<String, Object> dual = mapValue(rag.get("dualRoute"));
            BenchmarkStats dualStats = (BenchmarkStats) dual.get("stats");
            md.append("\n## RAG 评测\n\n");
            md.append("- 样本数：").append(rag.get("sampleCount")).append("\n");
            md.append("- 双路平均耗时：").append(dualStats == null ? 0 : dualStats.getAvgMs()).append(" ms，P95：")
                    .append(dualStats == null ? 0 : dualStats.getP95Ms()).append(" ms\n");
            md.append("- 双路相对单路关键词覆盖提升：").append(rag.get("dualVsSingleCoverageLift")).append(" 个百分点\n");
            md.append("- 分类过滤相对双路提升：").append(rag.get("filteredVsDualCoverageLift")).append(" 个百分点\n");
        }

        if (!web.isEmpty()) {
            BenchmarkStats totalStats = (BenchmarkStats) web.get("totalStats");
            md.append("\n## 联网搜索评测\n\n");
            md.append("- 样本数：").append(web.get("sampleCount")).append("\n");
            md.append("- 平均上下文构建耗时：").append(totalStats == null ? 0 : totalStats.getAvgMs()).append(" ms，P95：")
                    .append(totalStats == null ? 0 : totalStats.getP95Ms()).append(" ms\n");
            md.append("- 近似 Token 压缩率：").append(totalStats == null || totalStats.getAvgScore() == null ? 0 : totalStats.getAvgScore()).append("%\n");
        }

        if (!chat.isEmpty()) {
            BenchmarkStats responseStats = (BenchmarkStats) chat.get("responseStats");
            BenchmarkStats firstTokenStats = (BenchmarkStats) chat.get("firstTokenStats");
            md.append("\n## 对话与 SSE 评测\n\n");
            md.append("- 样本数：").append(chat.get("sampleCount")).append("\n");
            md.append("- 平均首包时间：").append(firstTokenStats == null ? 0 : firstTokenStats.getAvgMs()).append(" ms\n");
            md.append("- 平均完整响应时间：").append(responseStats == null ? 0 : responseStats.getAvgMs()).append(" ms，P95：")
                    .append(responseStats == null ? 0 : responseStats.getP95Ms()).append(" ms\n");
            md.append("- Tool Calling 降级成功率：").append(chat.get("fallbackSuccessRate")).append("%\n");
        }

        if (!recentRuns.isEmpty()) {
            md.append("\n## 最近运行样本\n\n");
            for (AgentRunMetrics run : recentRuns) {
                md.append("- `").append(run.getRequestId()).append("` / ").append(run.getScene())
                        .append(" / first=").append(run.getFirstTokenLatencyMs()).append(" ms / total=")
                        .append(run.getTotalDurationMs()).append(" ms / fallback=")
                        .append(run.isFallbackTriggered() ? "是" : "否")
                        .append(" / success=").append(run.isSuccess() ? "是" : "否")
                        .append("\n");
            }
        }

        List<String> bullets = buildResumeBullets(rag, web, chat, baseline);
        return AgentMetricReport.builder()
                .generatedAt(TIME_FORMATTER.format(LocalDateTime.now()))
                .markdown(md.toString().trim())
                .resumeBullets(bullets)
                .build();
    }

    private List<String> buildResumeBullets(Map<String, Object> rag,
                                            Map<String, Object> web,
                                            Map<String, Object> chat,
                                            Map<String, Object> baseline) {
        List<String> bullets = new ArrayList<>();
        bullets.add("统一对话与简历优化两条链路的 Agent 编排，抽象 AgentContext、意图识别、上下文组装、Prompt 构建、Planner 决策与 Tool Calling 降级链路，沉淀 6 个核心步骤。");
        if (!rag.isEmpty()) {
            Map<String, Object> dual = mapValue(rag.get("dualRoute"));
            BenchmarkStats stats = (BenchmarkStats) dual.get("stats");
            bullets.add("基于 PostgreSQL pgvector 实现知识库 RAG 检索，支持双路召回、TopK 控制与分类过滤；离线样本评测下双路召回平均耗时 "
                    + (stats == null ? 0 : stats.getAvgMs()) + " ms，关键词覆盖率较单路提升 "
                    + valueText(rag.get("dualVsSingleCoverageLift")) + " 个百分点。");
        }
        if (!web.isEmpty()) {
            BenchmarkStats stats = (BenchmarkStats) web.get("totalStats");
            bullets.add("自部署 SearXNG 聚合搜索服务，结合网页并发抓取与正文清洗构建联网上下文；样本评测平均耗时 "
                    + (stats == null ? 0 : stats.getAvgMs()) + " ms，近似 Token 压缩率 "
                    + (stats == null || stats.getAvgScore() == null ? 0 : stats.getAvgScore()) + "%。");
        }
        if (!chat.isEmpty()) {
            BenchmarkStats firstTokenStats = (BenchmarkStats) chat.get("firstTokenStats");
            BenchmarkStats responseStats = (BenchmarkStats) chat.get("responseStats");
            bullets.add("实现基于 SSE 的流式对话、聊天记录持久化与历史会话管理；样本压测平均首包时间 "
                    + (firstTokenStats == null ? 0 : firstTokenStats.getAvgMs()) + " ms，平均完整响应时间 "
                    + (responseStats == null ? 0 : responseStats.getAvgMs()) + " ms，复杂问答降级成功率 "
                    + valueText(chat.get("fallbackSuccessRate")) + "%。");
        }
        if (!baseline.isEmpty()) {
            bullets.add("完成前后端联调与知识库闭环能力建设，支持知识库新增、编辑、向量回填、检索调试及运行指标验证，为项目简历量化提供可复现数据口径。");
        }
        return bullets;
    }

    private Map<String, Object> buildBenchmarkBlock(List<Map<String, Object>> items, BenchmarkStats stats) {
        return new LinkedHashMap<>(Map.of(
                "stats", stats,
                "items", items
        ));
    }

    private BenchmarkStats buildStatsWithExtra(List<Long> durations,
                                               int sampleCount,
                                               int successCount,
                                               List<Double> scores,
                                               List<Double> compressions) {
        List<Double> mergedScores = new ArrayList<>(scores);
        if (compressions != null && !compressions.isEmpty()) {
            mergedScores = compressions;
        }
        return BenchmarkStats.fromDurations(durations, sampleCount, successCount, mergedScores);
    }

    private Map<String, Object> buildRagItem(String route,
                                             RagSample sample,
                                             long durationMs,
                                             boolean success,
                                             double keywordCoverage,
                                             String context) {
        return new LinkedHashMap<>(Map.of(
                "route", route,
                "name", sample.name(),
                "query", sample.query(),
                "category", StringUtils.defaultString(sample.category()),
                "durationMs", durationMs,
                "keywordCoverage", keywordCoverage,
                "success", success,
                "contextPreview", preview(context)
        ));
    }

    private String formatKnowledgeContext(List<SearchResumeKnowledgeRspVO> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (SearchResumeKnowledgeRspVO item : items) {
            if (item != null && StringUtils.isNotBlank(item.getContent())) {
                sb.append(item.getContent()).append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    private AgentRunMetrics waitForRun(String traceId) {
        for (int i = 0; i < 20; i++) {
            AgentRunMetrics metrics = agentMetricsService.getRun(traceId);
            if (metrics != null) {
                return metrics;
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private String fetchRawHtml(String url) {
        if (StringUtils.isBlank(url)) {
            return "";
        }
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "text/html")
                .build();
        try (okhttp3.Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return "";
            }
            ResponseBody body = response.body();
            return body == null ? "" : body.string();
        } catch (Exception e) {
            return "";
        }
    }

    private boolean hasBean(String beanName) {
        return applicationContext.containsBean(beanName);
    }

    private boolean extensionExists(String extensionName) {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement("SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = ?)");
        ) {
            ps.setString(1, extensionName);
            try (var rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        } catch (Exception e) {
            log.warn("检测数据库扩展失败：{}", e.getMessage());
            return false;
        }
    }

    private boolean tableExists(String tableName) {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement("""
                     SELECT EXISTS(
                         SELECT 1
                         FROM information_schema.tables
                         WHERE table_name = ?
                     )
                     """)) {
            ps.setString(1, tableName);
            try (var rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        } catch (Exception e) {
            log.warn("检测数据表失败：table={}, reason={}", tableName, e.getMessage());
            return false;
        }
    }

    private double keywordCoverage(List<String> expectedKeywords, String context) {
        if (expectedKeywords == null || expectedKeywords.isEmpty() || StringUtils.isBlank(context)) {
            return 0D;
        }
        String safeContext = context.toLowerCase();
        long hit = expectedKeywords.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::toLowerCase)
                .filter(safeContext::contains)
                .count();
        return round(hit * 100D / expectedKeywords.size());
    }

    private int estimateTokens(String text) {
        if (StringUtils.isBlank(text)) {
            return 0;
        }
        int cjkCount = 0;
        int otherCount = 0;
        for (char ch : text.toCharArray()) {
            if (Character.isWhitespace(ch)) {
                continue;
            }
            Character.UnicodeScript script = Character.UnicodeScript.of(ch);
            if (script == Character.UnicodeScript.HAN) {
                cjkCount++;
            } else {
                otherCount++;
            }
        }
        return cjkCount + (int) Math.ceil(otherCount / 4D);
    }

    private String preview(String text) {
        return StringUtils.abbreviate(StringUtils.defaultString(Jsoup.parse(StringUtils.defaultString(text)).text()), 180);
    }

    private double avg(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0D;
        }
        return round(values.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0D));
    }

    private double round(double value) {
        return Math.round(value * 100D) / 100D;
    }

    private int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double doubleValue(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean boolValue(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(String.valueOf(value).trim());
    }

    private String stringValue(Object value, String defaultValue) {
        String text = value == null ? null : String.valueOf(value).trim();
        return StringUtils.defaultIfBlank(text, defaultValue);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).filter(StringUtils::isNotBlank).toList();
    }

    private String boolText(Object value) {
        return Boolean.TRUE.equals(value) ? "已就绪" : "未就绪";
    }

    private String valueText(Object value) {
        return value == null ? "0" : String.valueOf(value);
    }

    private List<RagSample> parseRagSamples(Object value) {
        return mapList(value).stream()
                .map(item -> new RagSample(
                        stringValue(item.get("name"), stringValue(item.get("query"), "未命名样本")),
                        stringValue(item.get("query"), ""),
                        stringValue(item.get("category"), null),
                        stringList(item.get("expectedKeywords"))))
                .filter(item -> StringUtils.isNotBlank(item.query()))
                .toList();
    }

    private List<WebSample> parseWebSamples(Object value) {
        return mapList(value).stream()
                .map(item -> new WebSample(
                        stringValue(item.get("name"), stringValue(item.get("query"), "未命名样本")),
                        stringValue(item.get("query"), ""),
                        stringList(item.get("expectedKeywords"))))
                .filter(item -> StringUtils.isNotBlank(item.query()))
                .toList();
    }

    private List<ChatSample> parseChatSamples(Object value, String defaultModelName, double defaultTemperature) {
        return mapList(value).stream()
                .map(item -> new ChatSample(
                        stringValue(item.get("name"), stringValue(item.get("message"), "未命名样本")),
                        stringValue(item.get("message"), ""),
                        boolValue(item.get("knowledgeRag"), false),
                        boolValue(item.get("networkSearch"), false),
                        boolValue(item.get("searchToolEnabled"), true),
                        boolValue(item.get("agentPlanner"), true),
                        intValue(item.get("maxAgentSteps"), 3),
                        stringValue(item.get("modelName"), defaultModelName),
                        doubleValue(item.get("temperature"), defaultTemperature)))
                .filter(item -> StringUtils.isNotBlank(item.message()))
                .toList();
    }

    private List<RagSample> defaultRagSamples() {
        return List.of(
                new RagSample("简历项目表达", "Java 后端项目描述怎么写更有量化结果", "项目描述", List.of("量化", "项目", "STAR")),
                new RagSample("面试准备", "前端开发面试自我介绍如何组织", "面试", List.of("自我介绍", "面试", "结构")),
                new RagSample("职业发展", "中级工程师如何规划职业发展路径", "职业发展", List.of("职业", "阶段", "行动"))
        );
    }

    private List<WebSample> defaultWebSamples() {
        return List.of(
                new WebSample("后端趋势", "2026 Java 后端面试趋势", List.of("Java", "面试", "趋势")),
                new WebSample("前端趋势", "2026 前端岗位技能要求", List.of("前端", "技能", "岗位"))
        );
    }

    private List<ChatSample> defaultChatSamples(String modelName, double temperature) {
        return List.of(
                new ChatSample("简历优化问答", "帮我优化 Java 后端简历中的项目经历表述", true, false, true, true, 3, modelName, temperature),
                new ChatSample("面试准备问答", "前端开发面试应该怎么准备，请给我一份清单", true, false, true, true, 3, modelName, temperature),
                new ChatSample("联网信息问答", "2026 Java 后端岗位最新要求有哪些变化", false, true, true, true, 3, modelName, temperature)
        );
    }

    private record RagSample(String name, String query, String category, List<String> expectedKeywords) {}

    private record WebSample(String name, String query, List<String> expectedKeywords) {}

    private record ChatSample(String name,
                              String message,
                              boolean knowledgeRag,
                              boolean networkSearch,
                              boolean searchToolEnabled,
                              boolean agentPlanner,
                              int maxAgentSteps,
                              String modelName,
                              double temperature) {}
}
