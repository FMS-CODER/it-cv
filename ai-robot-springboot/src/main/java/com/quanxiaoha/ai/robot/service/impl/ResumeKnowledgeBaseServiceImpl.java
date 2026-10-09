package com.quanxiaoha.ai.robot.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.quanxiaoha.ai.robot.domain.dos.ResumeKnowledgeBaseDO;
import com.quanxiaoha.ai.robot.domain.mapper.ResumeKnowledgeBaseMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanxiaoha.ai.robot.model.vo.knowledge.FindResumeKnowledgePageListReqVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.FindResumeKnowledgePageListRspVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.ImportResumeKnowledgeReqVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.ImportResumeKnowledgeRspVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.ResumeKnowledgeItemVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.SearchResumeKnowledgeReqVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.SearchResumeKnowledgeRspVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.UpdateResumeKnowledgeReqVO;
import com.quanxiaoha.ai.robot.service.ResumeKnowledgeBaseService;
import com.quanxiaoha.ai.robot.utils.PageResponse;
import com.quanxiaoha.ai.robot.utils.Response;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 简历知识库实现：知识 CRUD、DashScope 向量化、pgvector 余弦距离检索、embedding 回填。
 */
@Service
@Slf4j
public class ResumeKnowledgeBaseServiceImpl implements ResumeKnowledgeBaseService {

    /**
     * DashScope text-embedding 单次最大输入 25 条，取 16 作为安全批量大小。
     */
    private static final int EMBED_BATCH_SIZE = 16;

    /**
     * embedding 回填默认每批处理条数。
     */
    private static final int REFILL_BATCH_DEFAULT = 16;

    /**
     * 相似度检索默认 Top-K 条数。
     */
    private static final int SEARCH_TOPK_DEFAULT = 5;

    @Resource
    private ResumeKnowledgeBaseMapper resumeKnowledgeBaseMapper;
    @Resource
    private DataSource dataSource;
    @Resource
    private ObjectMapper objectMapper;

    /**
     * Embedding 模型（由 spring-ai-alibaba-starter-dashscope 自动注入 DashScopeEmbeddingModel）。
     * 未配置 API Key 时为 null，此时导入/更新/检索跳过向量化。
     */
    @Autowired(required = false)
    private EmbeddingModel embeddingModel;

    /**
     * 从 pg_attribute 读取的 embedding 列维度（vector(N) 中的 N），0 表示未知。
     */
    private volatile int embeddingDimension = 0;

    /**
     * embedding 列的实际类型分类：
     * - "vector"：pgvector 原生 vector(N)
     * - "text"：普通 text 列（未启用 pgvector）
     * - null：尚未检测
     */
    private volatile String embeddingColumnType;

    /**
     * pg_catalog.format_type 返回的列类型全名，用于 SQL cast（如 vector(1024)），避免硬编码。
     */
    private volatile String embeddingPgFormatType;

    @PostConstruct
    public void init() {
        ensureEmbeddingColumnMeta();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<ImportResumeKnowledgeRspVO> importBatch(ImportResumeKnowledgeReqVO reqVO) {
        ensureEmbeddingColumnMeta();

        List<ResumeKnowledgeItemVO> items = reqVO.getItems();
        if (items == null || items.isEmpty()) {
            return Response.success(ImportResumeKnowledgeRspVO.builder().imported(0).build());
        }

        List<ResumeKnowledgeItemVO> validItems = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        for (ResumeKnowledgeItemVO item : items) {
            if (item == null || StringUtils.isBlank(item.getContent())) continue;
            String normalized = item.getContent().trim();
            validItems.add(ResumeKnowledgeItemVO.builder()
                    .content(normalized)
                    .category(item.getCategory())
                    .metadata(item.getMetadata())
                    .build());
            contents.add(normalized);
        }
        if (validItems.isEmpty()) {
            return Response.success(ImportResumeKnowledgeRspVO.builder().imported(0).build());
        }

        List<float[]> vectors = null;
        if (embeddingModel != null) {
            vectors = embedInBatches(contents);
            if (vectors == null || vectors.size() != validItems.size()) {
                return Response.fail("向量化结果数量与输入不匹配");
            }
            for (int i = 0; i < validItems.size(); i++) {
                String emb = toPgVectorString(vectors.get(i));
                if (StringUtils.isBlank(emb)) {
                    return Response.fail("第 " + (i + 1) + " 条向量化结果为空，请检查 DashScope 服务");
                }
            }
        }

        LocalDateTime now = LocalDateTime.now();
        int imported = 0;
        for (int i = 0; i < validItems.size(); i++) {
            ResumeKnowledgeItemVO item = validItems.get(i);
            String embedding = null;
            if (vectors != null && i < vectors.size()) {
                embedding = toPgVectorString(vectors.get(i));
            }

            ResumeKnowledgeBaseDO entity = ResumeKnowledgeBaseDO.builder()
                    .content(item.getContent())
                    .category(StringUtils.defaultIfBlank(item.getCategory(), "默认"))
                    .metadata(toJsonbString(item.getMetadata()))
                    .embedding(embedding)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            imported += resumeKnowledgeBaseMapper.insert(entity);
        }

        return Response.success(ImportResumeKnowledgeRspVO.builder().imported(imported).build());
    }

    @Override
    public Response<ImportResumeKnowledgeRspVO> importSamples() {
        List<ResumeKnowledgeItemVO> items = buildSampleItems();
        return importBatch(ImportResumeKnowledgeReqVO.builder().items(items).build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<Boolean> updateKnowledge(UpdateResumeKnowledgeReqVO reqVO) {
        ensureEmbeddingColumnMeta();

        ResumeKnowledgeBaseDO existed = resumeKnowledgeBaseMapper.selectById(reqVO.getId());
        if (existed == null) {
            return Response.fail("知识不存在");
        }

        String content = reqVO.getContent().trim();
        if (StringUtils.isBlank(content)) {
            return Response.fail("content 不能为空");
        }

        String embedding = null;
        if (embeddingModel != null) {
            List<float[]> vectors = embedInBatches(List.of(content));
            float[] vector = (vectors == null || vectors.isEmpty()) ? null : vectors.get(0);
            embedding = toPgVectorString(vector);
            if (StringUtils.isBlank(embedding)) {
                return Response.fail("重新向量化失败，请检查 DashScope 服务");
            }
        }

        existed.setContent(content);
        existed.setCategory(StringUtils.defaultIfBlank(reqVO.getCategory(), "默认"));
        existed.setMetadata(toJsonbString(reqVO.getMetadata()));
        existed.setEmbedding(embedding);
        existed.setUpdatedAt(LocalDateTime.now());

        int rows = resumeKnowledgeBaseMapper.updateById(existed);
        return Response.success(rows > 0);
    }

    @Override
    public PageResponse<FindResumeKnowledgePageListRspVO> pageList(FindResumeKnowledgePageListReqVO reqVO) {
        ensureEmbeddingColumnMeta();

        Long current = Objects.isNull(reqVO.getCurrent()) ? 1L : reqVO.getCurrent();
        Long size = Objects.isNull(reqVO.getSize()) ? 10L : reqVO.getSize();
        String category = reqVO.getCategory();

        Page<ResumeKnowledgeBaseDO> page = resumeKnowledgeBaseMapper.selectPageList(current, size, category);
        List<ResumeKnowledgeBaseDO> records = page.getRecords();

        List<FindResumeKnowledgePageListRspVO> vos = null;
        if (records != null && !records.isEmpty()) {
            vos = records.stream()
                    .map(r -> FindResumeKnowledgePageListRspVO.builder()
                            .id(r.getId())
                            .content(r.getContent())
                            .category(r.getCategory())
                            .metadata(r.getMetadata())
                            .createdAt(r.getCreatedAt())
                            .build())
                    .collect(Collectors.toList());
        }

        return PageResponse.success(page, vos);
    }

    @Override
    public Response<Integer> refillEmbeddings(Integer batchSize) {
        ensureEmbeddingColumnMeta();
        if (embeddingModel == null) {
            log.warn("未注入 EmbeddingModel，DashScope 可能未配置");
            return Response.success(0);
        }

        int size = (batchSize == null || batchSize <= 0) ? REFILL_BATCH_DEFAULT : Math.min(batchSize, EMBED_BATCH_SIZE);
        int totalUpdated = 0;
        try (Connection conn = dataSource.getConnection()) {
            while (true) {
                List<long[]> empty = new ArrayList<>();
                List<String> contents = new ArrayList<>();
                String selectSql = "SELECT id, content FROM resume_knowledge_base WHERE embedding IS NULL ORDER BY id ASC LIMIT " + size;
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(selectSql)) {
                    while (rs.next()) {
                        empty.add(new long[]{rs.getLong(1)});
                        contents.add(rs.getString(2));
                    }
                }
                if (contents.isEmpty()) break;

                List<float[]> vectors = embedInBatches(contents);
                if (vectors == null) break;

                String updateSql = "UPDATE resume_knowledge_base SET embedding = ?::" + sqlCastEmbeddingType()
                        + ", updated_at = CURRENT_TIMESTAMP WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    for (int i = 0; i < empty.size(); i++) {
                        float[] vec = i < vectors.size() ? vectors.get(i) : null;
                        String embed = toPgVectorString(vec);
                        if (embed == null) continue;
                        ps.setString(1, embed);
                        ps.setLong(2, empty.get(i)[0]);
                        ps.addBatch();
                    }
                    int[] res = ps.executeBatch();
                    for (int r : res) {
                        if (r > 0) totalUpdated += r;
                    }
                }

                if (empty.size() < size) break;
            }
        } catch (Exception e) {
            log.error("回填 embedding 异常: {}", e.getMessage(), e);
            return Response.fail("回填 embedding 失败: " + e.getMessage());
        }
        log.info("回填 embedding 完成，共更新 {} 条", totalUpdated);
        return Response.success(totalUpdated);
    }

    @Override
    public Response<List<SearchResumeKnowledgeRspVO>> searchSimilar(SearchResumeKnowledgeReqVO reqVO) {
        ensureEmbeddingColumnMeta();

        if (embeddingModel == null) {
            return Response.fail("未注入 EmbeddingModel，DashScope 可能未配置");
        }
        if (!"vector".equals(embeddingColumnType)) {
            return Response.fail("pgvector 未就绪：embedding 列需为 vector 类型（当前为 " + embeddingColumnType + "）");
        }

        int topK = (reqVO.getTopK() == null || reqVO.getTopK() <= 0) ? SEARCH_TOPK_DEFAULT : reqVO.getTopK();
        String category = reqVO.getCategory();

        float[] qv;
        try {
            qv = embeddingModel.embed(reqVO.getQuery());
        } catch (Exception e) {
            log.warn("query 向量化失败: {}", e.getMessage());
            return Response.fail("query 向量化失败: " + e.getMessage());
        }
        if (qv == null || qv.length == 0) {
            return Response.fail("query 向量化为空");
        }
        String qvStr = toPgVectorString(qv);

        String castT = sqlCastEmbeddingType();
        StringBuilder sql = new StringBuilder(
                "SELECT id, content, category, metadata::text AS metadata, " +
                        "       1 - (embedding <=> ?::" + castT + ") / 2 AS similarity " +
                        "FROM resume_knowledge_base " +
                        "WHERE embedding IS NOT NULL"
        );
        boolean hasCategory = StringUtils.isNotBlank(category);
        if (hasCategory) {
            sql.append(" AND category = ?");
        }
        sql.append(" ORDER BY embedding <=> ?::").append(castT).append(" ASC LIMIT ?");

        List<SearchResumeKnowledgeRspVO> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setString(idx++, qvStr);
            if (hasCategory) ps.setString(idx++, category);
            ps.setString(idx++, qvStr);
            ps.setInt(idx, topK);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(SearchResumeKnowledgeRspVO.builder()
                            .id(rs.getLong("id"))
                            .content(rs.getString("content"))
                            .category(rs.getString("category"))
                            .metadata(rs.getString("metadata"))
                            .similarity(rs.getDouble("similarity"))
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("相似度检索异常: {}", e.getMessage(), e);
            return Response.fail("相似度检索失败: " + e.getMessage());
        }
        return Response.success(result);
    }

    // ============================================================
    //  Embedding 工具方法
    // ============================================================

    /**
     * 按 EMBED_BATCH_SIZE 分批调用 DashScope 向量化，返回所有向量。
     * 若 embeddingModel 为 null 则返回全 null 列表。
     */
    private List<float[]> embedInBatches(List<String> contents) {
        if (embeddingModel == null || contents == null || contents.isEmpty()) {
            return Collections.nCopies(contents == null ? 0 : contents.size(), null);
        }
        List<float[]> all = new ArrayList<>(contents.size());
        for (int i = 0; i < contents.size(); i += EMBED_BATCH_SIZE) {
            List<String> part = contents.subList(i, Math.min(i + EMBED_BATCH_SIZE, contents.size()));
            List<float[]> batch = embedOnceSafely(part);
            all.addAll(batch);
        }
        if (embeddingDimension == 0) {
            for (float[] v : all) {
                if (v != null && v.length > 0) {
                    embeddingDimension = v.length;
                    log.info("DashScope embedding 实际维度: {}", embeddingDimension);
                    break;
                }
            }
        }
        return all;
    }

    /**
     * 单次调用 DashScope 向量化，失败时逐条重试。
     */
    private List<float[]> embedOnceSafely(List<String> part) {
        try {
            List<float[]> out = embeddingModel.embed(part);
            if (out != null && out.size() == part.size()) {
                return out;
            }
            log.warn("DashScope 批量 embedding 返回数量不匹配: 输入 {} 条，返回 {} 条",
                    part.size(), out == null ? 0 : out.size());
        } catch (Exception e) {
            log.warn("DashScope 批量 embedding 异常: {}", e.getMessage());
        }
        List<float[]> result = new ArrayList<>(part.size());
        for (String text : part) {
            try {
                result.add(embeddingModel.embed(text));
            } catch (Exception ex) {
                log.warn("DashScope 单条 embedding 失败: len={}, error={}",
                        text == null ? 0 : text.length(), ex.getMessage());
                result.add(null);
            }
        }
        return result;
    }

    /**
     * 将 float[] 转为 pgvector 格式字符串 "[0.1,0.2,...]"。
     * 维度不匹配时返回 null。
     */
    private String toPgVectorString(float[] vector) {
        if (vector == null || vector.length == 0) {
            return null;
        }
        if (embeddingDimension > 0 && vector.length != embeddingDimension) {
            log.warn("embedding 维度不匹配: 期望 {}，实际 {}，跳过该向量",
                    embeddingDimension, vector.length);
            return null;
        }
        StringBuilder sb = new StringBuilder(vector.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }


    /**
     * 从 pg_catalog 查询 resume_knowledge_base.embedding 列的实际类型，初始化 embeddingColumnType / embeddingDimension。
     * 用于后续 SQL cast 时使用正确的类型名（如 vector(1024)），避免硬编码。
     */
    private void ensureEmbeddingColumnMeta() {
        if (embeddingColumnType != null) {
            return;
        }
        synchronized (this) {
            if (embeddingColumnType != null) {
                return;
            }
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                         SELECT pg_catalog.format_type(a.atttypid, a.atttypmod) AS data_type
                         FROM pg_attribute a
                         JOIN pg_class c ON c.oid = a.attrelid
                         WHERE c.relname = 'resume_knowledge_base'
                           AND a.attname = 'embedding'
                           AND a.attnum > 0
                         """);
                 ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    String type = rs.getString(1);
                    if (type != null) {
                        embeddingPgFormatType = type.trim();
                        if (type.contains("vector")) {
                            embeddingColumnType = "vector";
                            int l = type.indexOf('(');
                            int r = type.indexOf(')');
                            if (l > 0 && r > l) {
                                try {
                                    embeddingDimension = Integer.parseInt(type.substring(l + 1, r).trim());
                                } catch (NumberFormatException ignored) {
                                }
                            }
                            log.info("检测到 embedding 列类型: {}（维度 {}）", embeddingPgFormatType, embeddingDimension);
                        } else {
                            embeddingColumnType = "text";
                            log.info("embedding 列为 text，未使用 pgvector");
                        }
                    }
                } else {
                    log.warn("未找到 resume_knowledge_base.embedding 列，请确认表结构");
                }
            } catch (Exception e) {
                log.warn("读取 embedding 列元数据失败: {}", e.getMessage());
            }
        }
    }

    /**
     * SQL cast 使用的类型名，优先使用 pg 元数据中的全名，避免 cv.vector 未创建时报错。
     */
    private String sqlCastEmbeddingType() {
        if (StringUtils.isNotBlank(embeddingPgFormatType)) {
            return embeddingPgFormatType;
        }
        if ("vector".equals(embeddingColumnType) && embeddingDimension > 0) {
            return "vector(" + embeddingDimension + ")";
        }
        if (StringUtils.isBlank(embeddingColumnType)) {
            return "vector";
        }
        return embeddingColumnType;
    }

    // ============================================================
    //  Metadata / 示例数据
    // ============================================================

    /**
     * 将 JsonNode metadata 转为 JSONB 字符串，null 时返回 null。
     */
    private String toJsonbString(JsonNode metadata) {
        if (metadata == null || metadata.isNull()) {
            return null;
        }
        try {
            if (metadata.isTextual()) {
                String raw = metadata.asText();
                if (StringUtils.isBlank(raw)) {
                    return null;
                }
                String trimmed = raw.trim();
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                    return trimmed;
                }
                return objectMapper.writeValueAsString(raw);
            }
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.warn("metadata 序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private List<ResumeKnowledgeItemVO> buildSampleItems() {
        List<ResumeKnowledgeItemVO> items = new ArrayList<>();

        items.add(ResumeKnowledgeItemVO.builder()
                .category("简历结构")
                .metadata(parseJsonNode("{\"来源\":\"系统预设\",\"类型\":\"模板\",\"标签\":[\"结构\",\"排版\",\"规范\"]}"))
                .content("""
                        简历排版规范
                        1. 每段经历控制在 1 页以内，每段 1-2 个要点
                        2. 使用动词开头：负责/主导/优化/设计/重构/推动
                        3. 结果导向：用数据说话 + 对比 + 量化产出 + 体现影响力
                        4. 排版原则：对齐 + 留白 + 字体统一
                        """.trim())
                .build());

        items.add(ResumeKnowledgeItemVO.builder()
                .category("简历结构")
                .metadata(parseJsonNode("{\"来源\":\"系统预设\",\"类型\":\"方法论\",\"标签\":[\"STAR\",\"案例\",\"数据量化\"]}"))
                .content("""
                        STAR 法则详解
                        - S（情境）：项目背景/业务痛点
                        - T（任务）：负责的目标
                        - A（行动）：具体方案/技术选型/架构设计
                        - R（结果）：量化收益/性能提升/业务增长

                        示例：
                        某 XX 项目通过 S 发现 P99 延迟从 800ms 升至 2000ms，T 要求优化 SQL 查询性能，A 引入缓存 + 索引优化，R 实现 P99 降至 180ms，吞吐量提升 92%。
                        """.trim())
                .build());

        items.add(ResumeKnowledgeItemVO.builder()
                .category("技巧")
                .metadata(parseJsonNode("{\"来源\":\"系统预设\",\"类型\":\"技巧\",\"标签\":[\"优化\",\"关键词\"]}"))
                .content("""
                        简历优化第 1 原则：关键词匹配
                        - 仔细阅读 JD，提取高频关键词
                        - 将关键词自然融入工作经历中
                        - 不要简单堆砌，要用项目经历佐证
                        - 针对不同岗位定制不同版本

                        避免空洞描述如"负责维护"改为"主导重构"
                        """.trim())
                .build());

        items.add(ResumeKnowledgeItemVO.builder()
                .category("技巧")
                .metadata(parseJsonNode("{\"来源\":\"系统预设\",\"类型\":\"技能\",\"标签\":[\"Java\",\"后端\",\"技术栈\"]}"))
                .content("""
                        Java 后端常见技术栈
                        - 扎实掌握 Java、JVM 调优经验
                        - 熟练 Spring Boot、Spring MVC、MyBatis/MyBatis-Plus
                        - 熟悉 Redis、MQ（Kafka/RabbitMQ）、ElasticSearch 等中间件
                        - 熟悉 PostgreSQL/MySQL，具备复杂 SQL 优化能力
                        - 熟悉 Git、CI/CD、Docker 等工程化工具

                        建议按"熟练/熟悉/了解"分级描述
                        """.trim())
                .build());

        return items;
    }

    private JsonNode parseJsonNode(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
