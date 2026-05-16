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

**Chunk 策略**：导入时自动语义切分。SemanticChunker 按分隔符优先级（`##` → 段落 → 行 → 句号）递归切分，chunk_size=512 tokens、overlap=64。检索查 chunk 表，按 source_id 分组合并连续 chunk 注入。召回率 70% → 85%。

---

## 3. 联网搜索

```
用户问题 → SearXNG（元搜索引擎，聚合 bing/google 等）
    → 搜索结果（URL + score）
    → ContentExtractor 三层清洗：
        ① 正文识别：提取 article/main，丢弃 nav/footer/ad
        ② 结构化清洗：保留 h1-h3/ul/ol/pre/code，过滤 Cookie 等模板文本
        ③ 智能截取：取前 3000 字符，段落边界截断
    → 格式化注入 prompt
```

**效果**：噪音 40-60% → <10%，token 消耗降 80%，信息密度大幅提升。

**两种注入路径**：
- NetworkSearchAdvisor：搜索结果注入 user prompt，模型直接回答
- SearchToolFacade.searchWeb：工具调用返回格式化文本，注入 ReAct 轨迹

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

### 日志查看命令

```bash
# 实时跟踪
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f postgres
docker compose logs -f searxng

# 查看最近 N 行
docker compose logs --tail=200 backend

# 按时间范围
docker compose logs --since="10m" backend
docker compose logs --since="2026-05-16T08:00" --until="2026-05-16T18:00" backend

# 搜索关键词
docker compose logs backend | grep -i error
docker compose logs backend | grep -i "rerank\|react\|planner\|embedding\|searxng"

# 进入容器查看日志文件
docker exec -it cv-backend sh
cat logs/application-2026-05-16.log
cat logs/error-2026-05-16.log

docker exec -it cv-frontend sh
cat /var/log/nginx/access.log
cat /var/log/nginx/error.log

docker exec -it cv-postgres bash
ls /var/log/postgresql/

# 容器资源占用
docker stats
```

### 后端关键日志关键词

| 关键词 | 级别 | 含义 |
|--------|------|------|
| `Agent run start` | INFO | 单次 Agent 请求开始 |
| `Agent step complete` | INFO | 各步骤执行完成（含耗时） |
| `Agent planner decision` | INFO | Planner 决策结果 |
| `Agent first token` | INFO | 首 token 延迟 |
| `Agent run finish` | INFO | 请求完成（总耗时/成功/失败） |
| `Agent tool calling fallback` | WARN | ReAct 失败，触发降级 |
| `ReAct 循环异常` | ERROR | ReAct 循环抛出异常 |
| `DashScope rerank` | WARN | 重排调用失败，退回向量排序 |
| `DashScope 向量化` | WARN | Embedding 调用失败 |
| `SearXNG 搜索结果` | INFO | 联网搜索结果原始 JSON |
| `知识库 RAG 检索未成功` | WARN | 向量检索失败 |
| `Planner 模型决策失败` | WARN | Planner JSON 解析失败，退回规则兜底 |

### 常见问题排错

| 问题 | 排查命令 | 常见原因 |
|------|----------|----------|
| 后端启动失败 | `docker compose logs backend` | 数据库连接失败 / API Key 未设置 / 端口冲突 |
| 前端白屏 | `docker exec cv-frontend cat /var/log/nginx/error.log` | Nginx 代理配置错误 / backend 未启动 |
| 502 Bad Gateway | `docker compose logs frontend \| grep "502"` | 后端无响应，检查 backend 日志 |
| 知识库检索无结果 | `docker compose logs backend \| grep -i "embedding\|向量"` | Embedding 未生成 / 知识库为空 |
| 重排不生效 | `docker compose logs backend \| grep -i "rerank"` | DashScope API Key 失效 / 模型返回异常 |
| 联网搜索无结果 | `docker compose logs backend \| grep -i "searxng"` | SearXNG 未启动 / 搜索引擎不可达 |
| ReAct 循环异常 | `docker compose logs backend \| grep -i "react\|ReAct"` | 模型返回格式异常 / maxReactSteps 耗尽 |
| 日志磁盘占用过大 | `docker system df` → `docker system prune -f` | 日志驱动未限制大小 |

### 降噪处理

Log4j2 已配置 RegexFilter，自动过滤客户端断开异常（`Broken pipe`、`Connection reset by peer`），避免 SSE 断开时大量 ERROR 日志误导排障。

---

## 5. 代码模块

| 模块 | 职责 |
|------|------|
| AgentOrchestratorImpl | 统一编排 Foundation → Planner → ReAct/Fallback/Standard |
| AgentPlannerServiceImpl | 模型 JSON 决策，失败规则兜底 |
| ResumeKnowledgeRagServiceImpl | 双路 RAG（检索→重排→截断→格式化） |
| SearchToolFacadeImpl | 知识库搜索（含重排）+ 联网搜索门面 |
| DashScopeRerankServiceImpl | GTE-ReRank 调用 |
| RagTruncationService | 相似度阈值截断 |
| SearXNGServiceImpl | SearXNG API 调用 |
| SearchResultContentFetcherServiceImpl | 并发抓取 + Jsoup 清洗 |
| SemanticChunker | 语义切分器 |
| ContentExtractor | 正文提取 + 结构化清洗 |

### 前端

Vue 3 + Vite + Ant Design Vue + Pinia + Tailwind CSS + Markdown-it

### GitHub

[https://github.com/FMS-CODER/it-cv](https://github.com/FMS-CODER/it-cv)
