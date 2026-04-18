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
 * @Author: ??
 * @Date: 2026/4/17
 * @Version: v1.0.0
 * @Description: ?????
 */
@Service
@Slf4j
public class ResumeKnowledgeBaseServiceImpl implements ResumeKnowledgeBaseService {

    /**
     * DashScope ????????????DashScope text-embedding ???? 25 ????? 16?
     */
    private static final int EMBED_BATCH_SIZE = 16;

    /**
     * ?? embedding ???????
     */
    private static final int REFILL_BATCH_DEFAULT = 16;

    /**
     * ????? Top-K ???
     */
    private static final int SEARCH_TOPK_DEFAULT = 5;

    @Resource
    private ResumeKnowledgeBaseMapper resumeKnowledgeBaseMapper;
    @Resource
    private DataSource dataSource;
    @Resource
    private ObjectMapper objectMapper;

    /**
     * Embedding ???????
     * ????? spring-ai-alibaba-starter-dashscope ???? DashScopeEmbeddingModel?
     * ???????? API Key????? null?????????????? / ?? / ??????
     */
    @Autowired(required = false)
    private EmbeddingModel embeddingModel;

    /**
     * ??????? embedding ???? pg_attribute ? vector(N)????? embed ??????
     * ?? 0 ???????
     */
    private volatile int embeddingDimension = 0;

    /**
     * ????? embedding ???????
     * - "vector"?pgvector ????????? vector(N)
     * - "text"????? text?pgvector ????????
     * - null?????
     */
    private volatile String embeddingColumnType;

    /**
     * pg_catalog.format_type 得到的列类型全名，用于 SQL 中 ?::类型（如 vector(1536)、cv.vector），避免硬编码不存在的 cv.vector
     */
    private volatile String embeddingPgFormatType;

    @PostConstruct
    public void init() {
        // ????????????????? DDL????? DBA / SQL ?????
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

        // 1) ???????????
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

        // 2) ??????????? embedding?????????????????
        List<float[]> vectors = null;
        if (embeddingModel != null) {
            vectors = embedInBatches(contents);
            if (vectors == null || vectors.size() != validItems.size()) {
                return Response.fail("????????????????????????");
            }
            for (int i = 0; i < validItems.size(); i++) {
                String emb = toPgVectorString(vectors.get(i));
                if (StringUtils.isBlank(emb)) {
                    return Response.fail("??????? " + (i + 1) + " ????????????????? DashScope ??");
                }
            }
        }

        // 3) ??????? embedding?
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
                    .category(StringUtils.defaultIfBlank(item.getCategory(), "???"))
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
            return Response.fail("???????");
        }

        String content = reqVO.getContent().trim();
        if (StringUtils.isBlank(content)) {
            return Response.fail("content ????");
        }

        // ?????????????????????????????????????????
        String embedding = null;
        if (embeddingModel != null) {
            List<float[]> vectors = embedInBatches(List.of(content));
            float[] vector = (vectors == null || vectors.isEmpty()) ? null : vectors.get(0);
            embedding = toPgVectorString(vector);
            if (StringUtils.isBlank(embedding)) {
                return Response.fail("????????? DashScope ???????");
            }
        }

        existed.setContent(content);
        existed.setCategory(StringUtils.defaultIfBlank(reqVO.getCategory(), "???"));
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
            log.warn("??? EmbeddingModel?DashScope ?????????");
            return Response.success(0);
        }

        int size = (batchSize == null || batchSize <= 0) ? REFILL_BATCH_DEFAULT : Math.min(batchSize, EMBED_BATCH_SIZE);
        int totalUpdated = 0;
        try (Connection conn = dataSource.getConnection()) {
            while (true) {
                List<long[]> empty = new ArrayList<>();
                List<String> contents = new ArrayList<>();
                // 1) ??? embedding ?????
                String selectSql = "SELECT id, content FROM resume_knowledge_base WHERE embedding IS NULL ORDER BY id ASC LIMIT " + size;
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(selectSql)) {
                    while (rs.next()) {
                        empty.add(new long[]{rs.getLong(1)});
                        contents.add(rs.getString(2));
                    }
                }
                if (contents.isEmpty()) break;

                // 2) ??????
                List<float[]> vectors = embedInBatches(contents);
                if (vectors == null) break;

                // 3) ??
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

                // ?????????? size ??????
                if (empty.size() < size) break;
            }
        } catch (Exception e) {
            log.error("?? embedding ???{}", e.getMessage(), e);
            return Response.fail("?? embedding ???" + e.getMessage());
        }
        log.info("?? embedding ???{} ?", totalUpdated);
        return Response.success(totalUpdated);
    }

    @Override
    public Response<List<SearchResumeKnowledgeRspVO>> searchSimilar(SearchResumeKnowledgeReqVO reqVO) {
        ensureEmbeddingColumnMeta();

        if (embeddingModel == null) {
            return Response.fail("??? EmbeddingModel?DashScope??????????");
        }
        if (!"vector".equals(embeddingColumnType)) {
            return Response.fail("pgvector 未就绪：embedding 列需为 vector 类型（当前为 " + embeddingColumnType + "）");
        }

        int topK = (reqVO.getTopK() == null || reqVO.getTopK() <= 0) ? SEARCH_TOPK_DEFAULT : reqVO.getTopK();
        String category = reqVO.getCategory();

        // 1) ? query ???
        float[] qv;
        try {
            qv = embeddingModel.embed(reqVO.getQuery());
        } catch (Exception e) {
            log.warn("query ??????{}", e.getMessage());
            return Response.fail("query ??????" + e.getMessage());
        }
        if (qv == null || qv.length == 0) {
            return Response.fail("query ????");
        }
        String qvStr = toPgVectorString(qv);

        // 2) pgvector：cast 类型与列实际类型一致（来自 format_type，如 vector(1536)）
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
            log.error("???????{}", e.getMessage(), e);
            return Response.fail("???????" + e.getMessage());
        }
        return Response.success(result);
    }

    // ============================================================
    //  Embedding ??
    // ============================================================

    /**
     * ?????????? EMBED_BATCH_SIZE ???????????????
     * ??? list ???????????????? null?
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
        // ??????????????????? vector(N) ?????
        if (embeddingDimension == 0) {
            for (float[] v : all) {
                if (v != null && v.length > 0) {
                    embeddingDimension = v.length;
                    log.info("DashScope embedding ??????{}", embeddingDimension);
                    break;
                }
            }
        }
        return all;
    }

    /**
     * ?????? DashScope???????????????????????
     */
    private List<float[]> embedOnceSafely(List<String> part) {
        try {
            List<float[]> out = embeddingModel.embed(part);
            if (out != null && out.size() == part.size()) {
                return out;
            }
            log.warn("DashScope ?? embedding ????????? {}??? {}??????",
                    part.size(), out == null ? 0 : out.size());
        } catch (Exception e) {
            log.warn("DashScope ?? embedding ?????????{}", e.getMessage());
        }
        // ?????
        List<float[]> result = new ArrayList<>(part.size());
        for (String text : part) {
            try {
                result.add(embeddingModel.embed(text));
            } catch (Exception ex) {
                log.warn("DashScope ?? embedding ?????={}????????{}",
                        text == null ? 0 : text.length(), ex.getMessage());
                result.add(null);
            }
        }
        return result;
    }

    /**
     * ? float[] ???? pgvector ??????????"[0.1,0.2,...]"?
     * - ??????null / ???? null
     * - ??????????????? null????? pgvector ??????
     */
    private String toPgVectorString(float[] vector) {
        if (vector == null || vector.length == 0) {
            return null;
        }
        if (embeddingDimension > 0 && vector.length != embeddingDimension) {
            log.warn("embedding ???????? {}??? {}????????????????",
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

    // ============================================================
    //  ??????????? DDL?
    // ============================================================

    /**
     * ?? resume_knowledge_base.embedding ???????????? pg_attribute?????
     * ?????? SQL ? "?::vector" / "?::text" ??????????????
     * ?? embeddingColumnType ???????????????????????
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
                    log.warn("???????? resume_knowledge_base.embedding ??????????");
                }
            } catch (Exception e) {
                log.warn("?? embedding ??????????????{}", e.getMessage());
            }
        }
    }

    /**
     * SQL 中 cast 使用的类型名，必须与 {@link #embeddingPgFormatType}（pg 元数据）一致，避免 cv.vector 未创建时报错。
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
    //  Metadata / ????
    // ============================================================

    /**
     * ?????? metadata ???? JSONB ????????? null??
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
            log.warn("metadata ??????????{}", e.getMessage());
            return null;
        }
    }

    private List<ResumeKnowledgeItemVO> buildSampleItems() {
        List<ResumeKnowledgeItemVO> items = new ArrayList<>();

        items.add(ResumeKnowledgeItemVO.builder()
                .category("????")
                .metadata(parseJsonNode("{\"??\":\"????\",\"??\":\"??\",\"??\":[\"??\",\"??\",\"??\"]}"))
                .content("""
                        ???????
                        1?? 1 ??????? 1 ???? 1-2 ???
                        2??????????/??/??/??/?????
                        3?????????? ? ??? ? ???? ? ????????? ???
                        4????????? + ?? + ???????????
                        """.trim())
                .build());

        items.add(ResumeKnowledgeItemVO.builder()
                .category("????")
                .metadata(parseJsonNode("{\"??\":\"????\",\"??\":\"???\",\"??\":[\"STAR\",\"??\",\"????\"]}"))
                .content("""
                        STAR ?????
                        - S?????????/????
                        - T???????????
                        - A?????????????????/??/???
                        - R?????????????????/??/??/????

                        ???
                        ? XX ??????S??????? P99 ? 800ms ?? 200ms?T?????????SQL ?????????A???? P99 ?? 180ms?????? 92%?R??
                        """.trim())
                .build());

        items.add(ResumeKnowledgeItemVO.builder()
                .category("??")
                .metadata(parseJsonNode("{\"??\":\"????\",\"??\":\"??\",\"??\":[\"??\",\"????\"]}"))
                .content("""
                        ???????1 ??????
                        - ??????/??/????
                        - ??????2 ????????????
                        - ????????????????????????
                        - ??????????????

                        ?????????????"??"???"??"???
                        """.trim())
                .build());

        items.add(ResumeKnowledgeItemVO.builder()
                .category("??")
                .metadata(parseJsonNode("{\"??\":\"????\",\"??\":\"??\",\"??\":[\"Java\",\"??\",\"???\"]}"))
                .content("""
                        Java ??????????
                        - ??????Java?JVM??????
                        - ???Spring Boot?Spring MVC?MyBatis/MyBatis-Plus
                        - ????Redis?MQ?Kafka/RabbitMQ??ElasticSearch????
                        - ????PostgreSQL/MySQL???????SQL ???
                        - ????Git?CI/CD?Docker?????????

                        ????"??/??/??"??????????
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
