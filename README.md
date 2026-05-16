# IT-CV 智能简历工作台

AI 驱动的简历优化与职业发展助手，集成 **ReAct 多工具调用**、**RAG 检索增强生成**、**语义重排**、**联网搜索** 等能力。

---

## 目录

1. [状态流与工具调用组织](#1-状态流与工具调用组织)
2. [RAG 数据源与处理流程](#2-rag-数据源与处理流程)
3. [SearXNG 联网搜索接入](#3-searxng-联网搜索接入)
4. [Docker 编排结构](#4-docker-编排结构)
5. [代码模块说明](#5-代码模块说明)

---

## 1. 状态流与工具调用组织

### 1.1 整体流程

```
用户输入
    │
    ▼
┌─────────────────────────────────────────────────┐
│  Foundation Steps（基础准备）                      │
│                                                   │
│  ① IntentClassifierStep                          │
│     → 识别意图：GENERAL_CHAT / RESUME_OPTIMIZE    │
│       / INTERVIEW_HELP / CAREER_ADVICE            │
│                                                   │
│  ② ContextAssemblerStep                          │
│     → 若启用 RAG，调用双路检索→重排→截断→注入     │
│                                                   │
│  ③ PromptBuilderStep                             │
│     → 根据场景+意图+RAG上下文构建 system/user prompt│
└─────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────┐
│  Planner 决策                                     │
│                                                   │
│  模型输出 JSON 决策：                              │
│  - intent / needKnowledgeSearch / needWebSearch   │
│  - initialToolCall / maxReactSteps / toolStrategy │
│  - toolQuery / responseStyle / reason             │
│                                                   │
│  失败时退回规则兜底（关键词匹配）                   │
└─────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────┐
│  工具调用模式（三选一）                             │
│                                                   │
│  ┌─ ReAct 多工具循环（默认）                      │
│  │  每轮模型可同时调用多个工具                     │
│  │  (knowledge_search + web_search)               │
│  │  根据轨迹自主决定是否继续                       │
│  │                                                 │
│  ├─ Fallback 降级流程                              │
│  │  ReAct 失败时，Planner 决策后                   │
│  │  一次性调用工具，结果注入 system prompt          │
│  │                                                 │
│  └─ Standard 标准流                                │
│     不调用工具，直接流式生成回答                    │
└─────────────────────────────────────────────────┘
    │
    ▼
流式输出 → 首 token 计时 → 落库 → 完成审计
```

### 1.2 AgentContext 核心字段

| 字段 | 说明 |
|------|------|
| `scene` | 业务场景：CHAT / RESUME_OPTIMIZE |
| `intent` | 用户意图：GENERAL_CHAT / RESUME_OPTIMIZE / INTERVIEW_HELP / CAREER_ADVICE |
| `plannerDecision` | Planner 决策结果 |
| `ragContext` | RAG 检索拼接后的上下文 |
| `systemPrompt` / `userPrompt` | 最终送入模型的提示词 |
| `reactTraces` | ReAct 多轮轨迹记录 |
| `reactFinished` / `reactFinalAnswer` | ReAct 结束标志与最终回答 |
| `searchToolEnabled` | 是否允许调用搜索工具 |
| `maxAgentSteps` | 最大执行步数 |

### 1.3 ReAct 多工具调用

每轮 ReAct 循环中，模型输出结构化文本：

```
思考：<推理过程>
工具调用：[{"tool":"knowledge_search","args":"..."},{"tool":"web_search","args":"..."}]
```

- 支持**每轮并行调用多个工具**（knowledge_search + web_search）
- 工具结果拼接为 observation，注入下一轮
- 模型输出 `最终回答：` 时结束循环
- `maxReactSteps` 由 Planner 控制（通常 1~2 步）

### 1.4 Planner 决策

Planner 通过模型输出 JSON 决策：

```json
{
  "intent": "INTERVIEW_HELP",
  "needKnowledgeSearch": true,
  "needWebSearch": false,
  "initialToolCall": true,
  "maxReactSteps": 1,
  "toolStrategy": "parallel",
  "toolQuery": "Java后端面试常见问题",
  "responseStyle": "结构化、可执行、偏实战",
  "reason": "用户询问面试相关"
}
```

失败时自动退回**规则兜底**（关键词匹配）。

---

## 2. RAG 数据源与处理流程

### 2.1 数据源

知识库数据存储在 PostgreSQL `resume_knowledge_base` 表：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键 |
| `content` | TEXT | 知识正文（简历写法、STAR 法则、面试话术等） |
| `category` | VARCHAR | 分类：简历通用 / 面试 / 项目描述 |
| `metadata` | JSONB | 元数据 |
| `embedding` | VECTOR(1536) | DashScope text-embedding-v2 向量 |

数据来源：
- **批量导入**：通过管理后台导入自定义知识条目
- **一键示例**：内置典型简历优化示例数据
- **回填**：`refillEmbeddings` 接口为历史数据补全向量

### 2.2 Chunk 与 Embedding

- **Chunk 策略**：按知识条目粒度存储，每条 content 为一个独立片段（人工编写，无需自动切分）
- **Embedding 模型**：DashScope `text-embedding-v2`（1536 维）
- **批量处理**：每批 16 条并发调用 DashScope API
- **存储**：向量写入 pgvector `vector(1536)` 类型列

### 2.3 向量检索

```sql
SELECT id, content, category, metadata,
       1 - (embedding <=> ?::vector(1536)) / 2 AS similarity
FROM resume_knowledge_base
WHERE embedding IS NOT NULL
  AND category = ?  -- 可选分类过滤
ORDER BY embedding <=> ?::vector(1536) ASC
LIMIT ?
```

- 使用 **余弦距离**（`<=>` 运算符）
- 映射到 `[0, 1]` 相似度分数
- 支持分类过滤

### 2.4 双路检索（RAG 链路）

```
用户输入
    │
    ├─ Q1 = 原始输入
    │      → 向量检索 topK × expandFactor / 2 条
    │
    └─ Q2 = 原始输入 + 输出锚点（如"请从简历撰写、面试表达角度补充"）
           → 向量检索 topK × expandFactor / 2 条
    │
    ▼
合并去重（按 id 去重，保留首次出现）
    │
    ▼
语义重排（DashScope GTE-ReRank）
    │
    ▼
相似度截断（threshold=0.35, maxTopK=8）
    │
    ▼
格式化注入 prompt
```

### 2.5 语义重排

- **服务**：DashScope GTE-ReRank
- **API**：`https://dashscope.aliyuncs.com/api/v1/services/rerank`
- **模型**：`gte-rerank`
- **输入**：query + 候选文档列表
- **输出**：按重排分数降序排列
- **失败回退**：自动退回向量分数排序

### 2.6 动态截断

```yaml
rag:
  truncation:
    similarity-threshold: 0.35  # 低于此阈值的片段丢弃
    max-top-k: 8                # 最多保留 8 条
```

- 优先使用重排分数（`rerankScore`），无重排时使用向量分数（`vectorScore`）
- 按分数降序遍历，低于阈值或超过上限时截断

### 2.7 工具调用链路的 RAG

`knowledge_search` 工具同样经过重排截断：

```
工具调用 → 扩大召回（topK × expandFactor）
         → 向量检索
         → 语义重排
         → 相似度截断
         → 格式化输出
```

---

## 3. SearXNG 联网搜索接入

### 3.1 架构

```
用户问题
    │
    ▼
SearXNG（元搜索引擎）
    │  聚合：bing / google / duckduckgo 等
    ▼
搜索结果列表（URL + score）
    │
    ▼
SearchResultContentFetcherService
    │  并发 HTTP 请求 + Jsoup 提取正文
    │  超时 7 秒，失败跳过
    ▼
搜索结果清洗
    │  - 过滤空内容
    │  - 按 score 排序
    │  - 限制 topK 条
    ▼
格式化注入 prompt
    "以下为联网搜索结果：
     ### 结果 1
     - 链接：https://...
     - 相关性：0.95
     正文内容..."
```

### 3.2 搜索结果清洗

1. **SearXNG 返回**：JSON 格式，含 `url` / `score` / `title`
2. **并发抓取**：`SearchResultContentFetcherService` 使用独立线程池并发请求每个 URL
3. **HTML 清洗**：Jsoup 提取纯文本，去除 HTML 标签
4. **超时处理**：单请求超时 7 秒，超时返回空
5. **过滤**：空内容条目跳过，保留有正文的结果

### 3.3 注入方式

两种注入路径：

| 路径 | 方式 |
|------|------|
| **NetworkSearchAdvisor** | 搜索结果注入 user prompt，模型直接回答 |
| **SearchToolFacade.searchWeb** | 工具调用返回格式化文本，注入 ReAct 轨迹 |

---

## 4. Docker 编排结构

### 4.1 服务拓扑

```
┌─────────────────────────────────────────────────────┐
│                    Docker Network                     │
│                                                       │
│  ┌──────────┐    ┌──────────┐    ┌──────────────┐   │
│  │ Frontend │───▶│ Backend  │───▶│ PostgreSQL   │   │
│  │ Nginx:80 │    │ Java:8080│    │ PGVector:5432│   │
│  └──────────┘    └──────────┘    └──────────────┘   │
│                       │                               │
│                       ▼                               │
│                  ┌──────────┐                        │
│                  │ SearXNG  │                        │
│                  │ :8888    │                        │
│                  └──────────┘                        │
└─────────────────────────────────────────────────────┘
```

### 4.2 服务配置

| 服务 | 镜像 | 端口 | 说明 |
|------|------|------|------|
| `postgres` | pgvector/pgvector:0.8.0-pg17 | 5432 | 主数据库 + 向量存储 |
| `searxng` | searxng/searxng:latest | 8888 | 元搜索引擎 |
| `backend` | 自定义构建 | 8080 | Spring Boot 应用 |
| `frontend` | 自定义构建 | 80 | Nginx 静态服务 |

### 4.3 环境变量

```bash
# 必需（API Key）
DASHSCOPE_API_KEY=sk-xxx          # DashScope API Key（用于 embedding + rerank）
DEEPSEEK_API_KEY=sk-xxx           # DeepSeek API Key（用于对话模型）

# 可选（数据库，已有默认值）
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/postgres?currentSchema=cv,public
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
SEARXNG_URL=http://searxng:8080/search
```

### 4.4 部署命令

```bash
# 启动所有服务
docker compose up -d

# 查看日志
docker compose logs -f backend
docker compose logs -f frontend

# 重启单个服务
docker compose restart backend

# 停止所有服务
docker compose down

# 完全清理（包括数据卷）
docker compose down -v
```

### 4.5 日志排错

| 问题 | 排查方法 |
|------|----------|
| 后端启动失败 | `docker compose logs backend` 查看 Spring Boot 启动日志 |
| 数据库连接失败 | 检查 `SPRING_DATASOURCE_URL` 中服务名是否为 `postgres` |
| SearXNG 无结果 | 检查 `docker compose logs searxng`，确认搜索引擎可达 |
| 前端白屏 | 检查 `docker compose logs frontend`，确认 Nginx 配置正确 |
| API Key 错误 | 确认环境变量 `DASHSCOPE_API_KEY` / `DEEPSEEK_API_KEY` 已设置 |
| 重排失败 | 后端日志搜索 `DashScope rerank`，确认 API 调用状态 |

---

## 5. 代码模块说明

### 后端模块（ai-robot-springboot）

| 模块 | 路径 | 职责 |
|------|------|------|
| **Agent 编排** | `agent/service/impl/AgentOrchestratorImpl.java` | 统一编排：Foundation → Planner → ReAct / Fallback / Standard |
| **Planner 决策** | `agent/service/impl/AgentPlannerServiceImpl.java` | 模型输出 JSON 决策，失败退回规则兜底 |
| **意图分类** | `agent/step/IntentClassifierStep.java` | 关键词匹配识别用户意图 |
| **RAG 装配** | `agent/step/ContextAssemblerStep.java` | 调用 RAG 服务组装上下文 |
| **Prompt 构建** | `agent/step/PromptBuilderStep.java` | 根据场景+意图构建 system/user prompt |
| **搜索工具门面** | `agent/service/impl/SearchToolFacadeImpl.java` | 知识库搜索（含重排）+ 联网搜索的统一门面 |
| **搜索工具** | `agent/tool/SearchAgentTools.java` | 暴露给 AI 模型的 @Tool 注解 |
| **RAG 服务** | `service/impl/ResumeKnowledgeRagServiceImpl.java` | 双路检索 → 重排 → 截断 → 格式化 |
| **知识库服务** | `service/impl/ResumeKnowledgeBaseServiceImpl.java` | 知识 CRUD + Embedding + 向量检索 |
| **重排服务** | `agent/service/impl/DashScopeRerankServiceImpl.java` | DashScope GTE-ReRank 调用 |
| **截断服务** | `agent/service/RagTruncationService.java` | 相似度阈值截断 |
| **联网搜索** | `service/impl/SearXNGServiceImpl.java` | SearXNG API 调用 |
| **内容抓取** | `service/impl/SearchResultContentFetcherServiceImpl.java` | 并发抓取 + Jsoup 清洗 |
| **ReAct 模型** | `agent/model/ReActStep.java` | 多工具调用轨迹记录 |
| **重排文档** | `agent/model/RankedDocument.java` | 含 vectorScore + rerankScore |
| **Agent 上下文** | `agent/model/AgentContext.java` | 单次请求全量运行时上下文 |
| **审计日志** | `agent/service/AgentAuditLogger.java` | 全链路耗时/状态记录 |

### 前端模块（ai-robot-vue3）

| 模块 | 说明 |
|------|------|
| Vue 3 + Vite | 前端框架 |
| Ant Design Vue | UI 组件库 |
| Pinia | 状态管理 |
| Tailwind CSS | 样式 |
| Markdown-it + highlight.js | 消息渲染 |

### GitHub

[https://github.com/FMS-CODER/it-cv](https://github.com/FMS-CODER/it-cv)
