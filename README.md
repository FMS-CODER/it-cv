# IT-CV 智询工作台

AI 驱动的简历优化与职业发展助手，集成 ReAct 多工具调用、RAG 检索增强生成、语义重排、联网搜索。

---

## 功能特性

- **简历优化**：上传 PDF / DOCX 或直接粘贴文本，选择目标岗位与额外要求，流式返回结构化优化建议
- **智能对话**：多轮会话、SSE 流式输出、历史会话与消息持久化（PostgreSQL），支持重命名与删除
- **RAG 知识库**：DashScope Embedding + pgvector 余弦检索 + GTE 重排，支持分类过滤与双路检索
- **联网搜索**：SearXNG 元搜索聚合 + 三层正文清洗，为回答补充实时信息
- **Agent 编排**：Planner（模型 JSON 决策）+ ReAct 多工具循环 + 失败降级兜底 + 全链路审计日志
- **一键部署**：`docker compose` 统一编排 前端 / 后端 / PostgreSQL(pgvector) / SearXNG

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 21、Spring Boot 3.4.5、Spring AI 1.1.1、Spring AI Alibaba（DashScope）、MyBatis-Plus 3.5.12 |
| 前端 | Vue 3.5、Vite 6、Pinia、Vue Router、Ant Design Vue 4、Tailwind CSS 4、markdown-it + highlight.js、fetch-event-source |
| 存储 | PostgreSQL 17 + pgvector 0.8.0（对话持久化、向量检索） |
| AI 能力 | DeepSeek（对话，流式）、DashScope `text-embedding-v2`（向量化）、`gte-rerank`（重排） |
| 检索与解析 | SearXNG（元搜索）、Jsoup（正文抽取）、PDFBox / POI（简历解析） |
| 日志与工具 | Log4j2、Hutool、Guava、OkHttp、p6spy |
| 部署 | Docker、Docker Compose、Nginx |

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
| 扩大召回 | topK × expandFactor(2)，给重排留候选空间 |
| 重排 | DashScope GTE-ReRank，失败退向量排序 |
| 截断 | 阈值 0.35 + 最大保留 8 条，优先使用重排分数 |

**Chunk 策略**：

```
导入 → SemanticChunker（递归字符切分）
       ├─ 分隔符优先级：## → 段落 → 行 → 句号
       ├─ chunk_size=512 tokens, overlap=64
       ├─ 最短 100（过短合并），最长 1024（强制切）
       └─ 写入 chunk 表 + 独立 embedding

检索 → 查 chunk 表 → 按 source_id 分组 → 连续 chunk 合并注入
```

**为什么**：整条存储长文本语义模糊、短文本缺上下文。切分后召回率 70% → 85%。

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

---

## 6. 项目结构

```
it-cv/
├── ai-robot-springboot/              # 后端服务（Spring Boot）
│   ├── src/main/java/com/quanxiaoha/ai/robot/
│   │   ├── agent/                    # Agent 编排：Planner / ReAct / RAG / 重排 / 审计
│   │   ├── controller/               # 接口层：Chat / ResumeOptimize / ResumeKnowledgeBase
│   │   ├── service/                  # 业务层：对话、知识库、联网搜索
│   │   ├── advisor/                  # ChatClient 增强：记忆、流式日志落库
│   │   ├── config/                   # 配置：ChatClient / Memory / Cors / OkHttp / 线程池
│   │   ├── domain/                   # 实体与 Mapper（MyBatis-Plus）
│   │   └── model/                    # DTO / VO
│   └── src/main/resources/
│       ├── db/                       # 建表与示例数据脚本
│       ├── application*.yml          # 多环境配置（dev / prod）
│       └── log4j2.xml                # 日志配置（含 SSE 断开降噪）
├── ai-robot-vue3/                    # 前端应用（Vue 3 + Vite）
│   └── src/
│       ├── views/Index.vue           # 主页面（简历优化 / 智能对话）
│       ├── components/               # 会话列表、输入框、流式 Markdown 渲染等
│       ├── api/                      # 接口封装（普通请求 + SSE 流式）
│       ├── stores/                   # Pinia 状态（会话 / UI）
│       └── layouts/ router/          # 布局与路由
├── docker-compose.yml                # 一键编排：frontend / backend / postgres / searxng
├── .env                              # Docker 部署所需密钥（需自行创建，已 gitignore）
└── ai-robot-springboot/Dockerfile, ai-robot-vue3/Dockerfile
```

---

## 7. 快速开始

### 方式一：Docker Compose（推荐）

```bash
# 1) 在项目根目录创建 .env，填入密钥
DASHSCOPE_API_KEY=sk-xxxxxxxx
DEEPSEEK_API_KEY=sk-xxxxxxxx

# 2) 构建并启动全部服务
docker compose up -d --build

# 3) 查看状态与日志
docker compose ps
docker compose logs -f backend
```

启动后：

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost |
| 后端 | http://localhost:8080 |
| SearXNG | http://localhost:8888 |
| PostgreSQL | localhost:5432（postgres / postgres，schema `cv`） |

首次启动需初始化数据库表结构：使用任意 PostgreSQL 客户端连接 `localhost:5432`，依次执行 `ai-robot-springboot/src/main/resources/db/` 下的脚本（`create-vector-type-cv.sql`、`schema-all.sql`，可选 `seed-resume-kb.sql` 导入知识库示例数据）。

### 方式二：本地开发

环境要求：JDK 21、Maven 3.9+、Node.js 22+、PostgreSQL 17（含 pgvector 扩展）、SearXNG。

```bash
# 后端（默认端口 8080）
cd ai-robot-springboot
# 在 application-dev.yml 中配置数据库连接与 API Key
mvn spring-boot:run

# 前端（Vite 默认端口 5173）
cd ai-robot-vue3
npm install
npm run dev
```

> 接口层 `@CrossOrigin` 指定了来源 `http://localhost:5174`，本地联调时请让前端端口与之匹配，或按需调整跨域配置。

---

## 8. 环境变量与配置

Docker 部署所需环境变量（根目录 `.env`）：

| 变量 | 说明 | 必填 |
|------|------|------|
| `DASHSCOPE_API_KEY` | 阿里云百炼 DashScope Key，用于向量化与重排 | 是 |
| `DEEPSEEK_API_KEY` | DeepSeek Key，用于对话 | 是 |

应用关键配置项：

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `spring.ai.deepseek.chat.options.model` | 对话模型 | `deepseek-flash` |
| `spring.ai.dashscope.embedding.options.model` | 向量化模型 | `text-embedding-v2` |
| `rag.rerank.model` | 重排模型 | `gte-rerank` |
| `rag.rerank.enabled` | 是否启用重排 | `true` |
| `rag.truncation.similarity-threshold` | 相似度阈值 | `0.35` |
| `rag.truncation.max-top-k` | 最大保留条数 | `8` |
| `rag.retrieval.expand-factor` | 召回扩大系数 | `2` |
| `searxng.url` | SearXNG 搜索地址 | `http://localhost:8888/search` |
| `searxng.count` | 搜索结果条数 | `10` |

---

## 9. API 接口

### 对话（`/chat`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/chat/new` | 新建会话 |
| POST | `/chat/completion` | 流式对话（SSE） |
| POST | `/chat/list` | 会话分页列表 |
| POST | `/chat/message/list` | 会话消息分页 |
| POST | `/chat/summary/rename` | 重命名会话摘要 |
| POST | `/chat/delete` | 删除会话及消息 |

```jsonc
// POST /chat/completion
{ "message": "帮我优化 Java 后端简历", "chatId": "uuid", "modelName": "deepseek-reasoner", "temperature": 0.8 }
```

### 简历优化（`/resume-optimize`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/resume-optimize/upload` | 上传并解析 PDF / DOCX（≤ 3MB） |
| POST | `/resume-optimize/optimize` | 流式优化简历（SSE） |

```jsonc
// POST /resume-optimize/optimize
{
  "resumeText": "……", "targetPosition": "Java 后端",
  "additionalRequirements": "突出性能优化经验",
  "knowledgeRag": true, "kbCategory": "后端", "kbTopK": 5,
  "searchToolEnabled": true, "agentPlanner": true, "maxAgentSteps": 3
}
```

### 简历知识库（`/resume-kb`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/resume-kb/import` | 批量导入知识 |
| POST | `/resume-kb/import/samples` | 导入内置示例数据 |
| POST | `/resume-kb/update` | 更新单条并重算向量 |
| POST | `/resume-kb/page/list` | 分页查询 |
| POST | `/resume-kb/embedding/refill` | 回填历史数据向量字段 |
| POST | `/resume-kb/search` | 向量相似度检索 |

```jsonc
// POST /resume-kb/search
{ "query": "STAR 法则怎么写项目经历", "topK": 5, "category": "项目描述" }
```


