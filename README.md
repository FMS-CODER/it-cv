# IT-CV 智询工作台

AI 驱动的简历优化与职业发展助手，集成 ReAct 多工具调用、RAG 检索增强生成、语义重排、联网搜索。

---

## 1. 状态流与工具调用

```
用户输入 → IntentClassifier → ContextAssembler(RAG) → PromptBuilder
    → Planner（模型 JSON 决策 / 规则兜底）
    → 三种模式：
        ① ReAct 循环（默认）：每轮并行调用多个工具
        ② Fallback 降级：ReAct 失败，一次性调用
        ③ Standard 标准流：不调工具，直接生成
    → 流式输出 → 审计落库
```

**ReAct 每轮**：`思考：...` + 多个工具调用（knowledge_search + web_search）→ observation 注入下一轮 → `最终回答：` 结束。

**Planner**：模型输出 JSON（intent/needKnowledgeSearch/needWebSearch/maxReactSteps/toolStrategy），失败退关键词规则兜底。

---

## 2. RAG 全链路

| 环节 | 实现 |
|------|------|
| 数据源 | PostgreSQL `resume_knowledge_base`（content + category + embedding VECTOR(1536)） |
| Embedding | DashScope text-embedding-v2，每批 16 条并发 |
| 检索 | pgvector 余弦距离（`<=>`），支持分类过滤 |
| 双路检索 | Q1=原始输入 + Q2=原始输入+输出锚点，合并去重 |
| 扩大召回 | topK × expandFactor(2)，给重排留候选空间 |
| 重排 | DashScope GTE-ReRank，失败退向量排序 |
| 截断 | 阈值 0.35 + 最大保留 8 条，优先使用重排分数 |

**Chunk 策略**：导入时自动语义切分。 按分隔符优先级（`##` → 段落 → 行 → 句号）递归切分，chunk_size=512 tokens、overlap=64。检索查 chunk 表，按 source_id 分组合并连续 chunk 注入。

---

## 3. 联网搜索

```
用户问题 → SearXNG（元搜索引擎，聚合 bing/google 等）
    → 搜索结果（URL + score）
    → ContentExtractor 三层清洗：
        ① 正文识别：提取 article/main，丢弃 nav/footer/ad
        ② 结构化清洗：保留 h1-h3/ul/ol/pre/code，过滤 Cookie 等模板文本
        ③ 智能截取：取前 3000 字符，段落边界截断
    → 注入 prompt
```


---

## 4. Docker 编排

```
Frontend(Nginx:80) → Backend(Java:8080) → PostgreSQL(PGVector:5432)
                                        → SearXNG(:8888)
```

| 服务 | 镜像 | 端口 |
|------|------|------|
| postgres | pgvector/pgvector:0.8.0-pg17 | 5432 |
| searxng | searxng/searxng:latest | 8888 |
| backend | 自定义构建 | 8080 |
| frontend | 自定义构建 | 80 |

**必需环境变量**：`DASHSCOPE_API_KEY`、`DEEPSEEK_API_KEY`

### 日志体系

| 服务 | 日志输出 | 查看方式 | 持久化 |
|------|----------|----------|--------|
| backend | stdout + Log4j2 文件 | `docker compose logs backend` | 容器内 `./logs/`（application/error 两类，保留 30 天） |
| frontend | stdout + Nginx 文件 | `docker compose logs frontend` | 容器内 `/var/log/nginx/`（access.log / error.log） |
| postgres | stdout | `docker compose logs postgres` | 容器内 `/var/log/postgresql/` |
| searxng | stdout | `docker compose logs searxng` | 容器内 `/etc/searxng/` |



## 5. 代码模块

| 模块 | 职责 |
|------|------|
| AgentOrchestratorImpl | 统一编排 Foundation → Planner → ReAct/Fallback/Standard |
| AgentPlannerServiceImpl | 模型 JSON 决策，失败规则兜底 |
| ResumeKnowledgeRagServiceImpl |  RAG（检索→重排→截断→格式化） |
| SearchToolFacadeImpl | 知识库搜索（含重排）+ 联网搜索 |
| DashScopeRerankServiceImpl | 重排调用 |


