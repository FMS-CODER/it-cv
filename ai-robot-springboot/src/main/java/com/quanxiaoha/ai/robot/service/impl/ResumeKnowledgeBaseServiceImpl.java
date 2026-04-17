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
import com.quanxiaoha.ai.robot.service.ResumeKnowledgeBaseService;
import com.quanxiaoha.ai.robot.utils.PageResponse;
import com.quanxiaoha.ai.robot.utils.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @Author: 小明
 * @Date: 2026/4/17
 * @Version: v1.0.0
 * @Description: 简历知识库
 */
@Service
@Slf4j
public class ResumeKnowledgeBaseServiceImpl implements ResumeKnowledgeBaseService {

    @Resource
    private ResumeKnowledgeBaseMapper resumeKnowledgeBaseMapper;
    @Resource
    private DataSource dataSource;
    @Resource
    private ObjectMapper objectMapper;

    /**
     * Embedding 模型（可选）。
     * 说明：当前项目默认只引入了 DeepSeek Chat 模型；若你额外引入并配置了任意 Embedding 模型（如 OpenAI/Ollama 等），
     * Spring 会自动装配该 Bean，此处即可在导入时自动生成向量并落库。
     */
    @Autowired(required = false)
    private EmbeddingModel embeddingModel;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<ImportResumeKnowledgeRspVO> importBatch(ImportResumeKnowledgeReqVO reqVO) {
        ensureResumeKnowledgeTable();

        List<ResumeKnowledgeItemVO> items = reqVO.getItems();
        if (items == null || items.isEmpty()) {
            return Response.success(ImportResumeKnowledgeRspVO.builder().imported(0).build());
        }

        LocalDateTime now = LocalDateTime.now();
        int imported = 0;
        for (ResumeKnowledgeItemVO item : items) {
            if (item == null || StringUtils.isBlank(item.getContent())) {
                continue;
            }

            String content = item.getContent().trim();
            String embedding = buildEmbeddingString(content);

            ResumeKnowledgeBaseDO entity = ResumeKnowledgeBaseDO.builder()
                    .content(content)
                    .category(StringUtils.defaultIfBlank(item.getCategory(), "未分类"))
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
    public PageResponse<FindResumeKnowledgePageListRspVO> pageList(FindResumeKnowledgePageListReqVO reqVO) {
        ensureResumeKnowledgeTable();

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

    /**
     * 确保知识库表存在。
     * 说明：当前项目未引入 Flyway/Liquibase，这里用最小化方式自动补齐表结构，避免首次运行时导入失败。
     * - 优先尝试使用 pgvector 的 vector 类型（需要安装扩展）
     * - 若扩展不可用，则降级为 TEXT 类型（不影响“导入/查询”功能，后续可手动迁移到 vector）
     */
    private void ensureResumeKnowledgeTable() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 1) 检查表是否存在（优先 public schema）
            boolean exists = false;
            try (ResultSet rs = stmt.executeQuery("SELECT to_regclass('public.resume_knowledge_base')")) {
                if (rs.next()) {
                    exists = rs.getString(1) != null;
                }
            }
            if (exists) {
                // 已存在则尽量补齐缺失列（老表可能没有 embedding）
                ensureResumeKnowledgeTableColumns(stmt);
                return;
            }

            // 2) 尝试创建 pgvector 扩展（可能因为权限/未安装而失败，允许降级）
            boolean vectorOk = true;
            try {
                stmt.execute("CREATE EXTENSION IF NOT EXISTS vector");
            } catch (Exception e) {
                vectorOk = false;
                log.warn("pgvector 扩展不可用，将降级为 TEXT 存储 embedding：{}", e.getMessage());
            }

            // 3) 创建表（embedding 视情况选择类型）
            String embeddingType = vectorOk ? "vector(1536)" : "TEXT";
            String createTableSql = """
                    CREATE TABLE IF NOT EXISTS resume_knowledge_base (
                        id BIGSERIAL PRIMARY KEY,
                        content TEXT NOT NULL,
                        metadata JSONB,
                        embedding %s,
                        category VARCHAR(100),
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """.formatted(embeddingType);
            stmt.execute(createTableSql);

            // 4) 创建更新时间触发器（若已存在则跳过）
            stmt.execute("""
                    CREATE OR REPLACE FUNCTION update_updated_at_column()
                    RETURNS TRIGGER AS $$
                    BEGIN
                        NEW.updated_at = CURRENT_TIMESTAMP;
                        RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql;
                    """);

            stmt.execute("DROP TRIGGER IF EXISTS update_resume_kb_updated_at ON resume_knowledge_base");
            stmt.execute("""
                    CREATE TRIGGER update_resume_kb_updated_at
                        BEFORE UPDATE ON resume_knowledge_base
                        FOR EACH ROW
                        EXECUTE PROCEDURE update_updated_at_column();
                    """);

            // 5) 向量索引仅在 vector 可用时创建（hnsw 依赖 pgvector）
            if (vectorOk) {
                try {
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_resume_kb_embedding ON resume_knowledge_base USING hnsw (embedding vector_cosine_ops)");
                } catch (Exception e) {
                    // 不影响导入/查询
                    log.warn("创建向量索引失败（不影响导入/查询）：{}", e.getMessage());
                }
            }

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_resume_kb_category ON resume_knowledge_base(category)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_resume_kb_created_at ON resume_knowledge_base(created_at DESC)");
        } catch (Exception e) {
            // 如果数据库不可用，让全局异常处理返回统一失败即可（同时打日志）
            log.error("初始化 resume_knowledge_base 表失败", e);
            throw new RuntimeException(e);
        }
    }

    private void ensureResumeKnowledgeTableColumns(Statement stmt) {
        try {
            // 补齐 embedding 列（存在则跳过）
            stmt.execute("ALTER TABLE resume_knowledge_base ADD COLUMN IF NOT EXISTS embedding TEXT");
        } catch (Exception e) {
            // 不影响导入/查询
            log.warn("补齐 resume_knowledge_base.embedding 列失败（不影响导入/查询）：{}", e.getMessage());
        }
    }

    /**
     * 生成 embedding 并序列化成 pgvector 可接受的字符串格式。
     * - 未配置 EmbeddingModel 时返回 null（仅落文本，后续可离线补齐 embedding）
     */
    private String buildEmbeddingString(String content) {
        if (embeddingModel == null || StringUtils.isBlank(content)) {
            return null;
        }
        try {
            float[] vector = embeddingModel.embed(content);
            if (vector == null || vector.length == 0) {
                return null;
            }
            // pgvector 输入格式: [0.1,0.2,...]
            StringBuilder sb = new StringBuilder(vector.length * 8 + 2);
            sb.append('[');
            for (int i = 0; i < vector.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(vector[i]);
            }
            sb.append(']');
            return sb.toString();
        } catch (Exception e) {
            log.warn("生成 embedding 失败，将仅落库文本：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 将前端传入的 metadata 统一转成 JSONB 可写入的字符串（或 null）。
     * - 传对象：{"a":1} -> {"a":1}
     * - 传数组：[1,2] -> [1,2]
     * - 传字符串且内容像 JSON："{\"a\":1}"（注意这里是字符串内容）-> {"a":1}
     * - 传普通字符串："abc" -> "abc"（作为 JSON 字符串）
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
                // 作为 JSON 字符串
                return objectMapper.writeValueAsString(raw);
            }
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.warn("metadata 序列化失败，将置空：{}", e.getMessage());
            return null;
        }
    }

    private List<ResumeKnowledgeItemVO> buildSampleItems() {
        List<ResumeKnowledgeItemVO> items = new ArrayList<>();

        items.add(ResumeKnowledgeItemVO.builder()
                .category("简历通用")
                .metadata(parseJsonNode("{\"来源\":\"内置示例\",\"类型\":\"清单\",\"标签\":[\"结构\",\"排版\",\"通用\"]}"))
                .content("""
                        简历结构建议：
                        1）用 1 页为佳（应届可 1 页，社招 1-2 页）。
                        2）顶部信息包含：姓名/城市/电话/邮箱/求职意向。
                        3）模块顺序：教育背景 → 技能栈 → 项目经验 → 工作经历（如有）→ 其他。
                        4）每段经历用「动词 + 结果 + 量化」表达，避免空话。
                        """.trim())
                .build());

        items.add(ResumeKnowledgeItemVO.builder()
                .category("项目描述")
                .metadata(parseJsonNode("{\"来源\":\"内置示例\",\"类型\":\"方法论\",\"标签\":[\"STAR\",\"表达\",\"项目经验\"]}"))
                .content("""
                        STAR 写法模板：
                        - S（情境）：项目背景/业务痛点
                        - T（任务）：你负责的目标
                        - A（行动）：你做了哪些关键动作（技术/协作/推动）
                        - R（结果）：带来什么可量化收益（性能/成本/转化/稳定性）
                        
                        示例：
                        在 XX 活动高峰期（S），负责将接口 P99 从 800ms 降到 200ms（T），通过缓存分层、SQL 优化、异步化改造（A），最终 P99 降至 180ms，超时率下降 92%（R）。
                        """.trim())
                .build());

        items.add(ResumeKnowledgeItemVO.builder()
                .category("面试")
                .metadata(parseJsonNode("{\"来源\":\"内置示例\",\"类型\":\"话术\",\"标签\":[\"面试\",\"自我介绍\"]}"))
                .content("""
                        面试自我介绍（1 分钟）框架：
                        - 我是谁：年限/方向/核心技能
                        - 我做过什么：2 个代表性项目（各一句话）
                        - 我擅长什么：关键优势（性能、稳定性、交付、协作）
                        - 我想要什么：目标岗位与匹配点
                        
                        注意：避免从小学开始讲；用“结果”而不是“过程”收尾。
                        """.trim())
                .build());

        items.add(ResumeKnowledgeItemVO.builder()
                .category("后端")
                .metadata(parseJsonNode("{\"来源\":\"内置示例\",\"类型\":\"清单\",\"标签\":[\"Java\",\"后端\",\"技能栈\"]}"))
                .content("""
                        Java 后端简历技能区建议：
                        - 语言与基础：Java、JVM、并发、集合
                        - 框架：Spring Boot、Spring MVC、MyBatis/MyBatis-Plus
                        - 中间件：Redis、MQ（Kafka/RabbitMQ）、ElasticSearch（如有）
                        - 数据库：PostgreSQL/MySQL（索引、事务、SQL 优化）
                        - 工程化：Git、CI/CD、Docker（如有）、监控告警
                        
                        写法：用“熟悉/掌握/了解”分层，避免堆砌名词。
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

