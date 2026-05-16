# IT-CV 智询工作台

AI 驱动的简历优化与职业发展助手，集成 ReAct 多工具调用、RAG 检索增强生成、语义重排、联网搜索。

---

## 1. 状态流与工具调用组织

```
用户输入 → IntentClassifier → ContextAssembler(RAG) → PromptBuilder
    → Planner(模型JSON决策/规则兜底)
    → 三种模式:
        ① ReAct循环(默认): 每轮可并行调用多个工具(knowledge_search + web_search)
        ② Fallback降级: ReAct失败时，Planner决策后一次性调用工具
        ③ Standard标准流: 不调用工具，直接流式生成
    → 流式输出 → 审计落库
```

**AgentContext** 包含 30+ 字段：requestId、scene(CHAT/RESUME_OPTIMIZE)、intent、plannerDecision、ragContext、reactTraces 等。

**ReAct 每轮**：模型输出 `思考：...` + 多个工具调用 → 工具结果拼接为 observation 注入下一轮 → 输出 `最终回答：` 时结束。

**Planner 决策**：模型输出 JSON（intent/needKnowledgeSearch/needWebSearch/initialToolCall/maxReactSteps/toolStrategy），失败退回关键词规则兜底。

---

## 2. RAG 数据源与处理流程

**数据源**：PostgreSQL `resume_knowledge_base` 表（content TEXT、category VARCHAR、embedding VECTOR(1536)）

| 环节 | 实现 |
|------|------|
| **Chunk** | 按知识条目粒度存储，人工编写，无需自动切分 |
| **Embedding** | DashScope `text-embedding-v2`（1536维），每批16条并发 |
| **检索** | pgvector 余弦距离（`<=>`），支持分类过滤 |
| **双路检索** | Q1=原始输入 + Q2=原始输入+输出锚点，合并去重 |
| **扩大召回** | topK × expandFactor(2)，给重排留候选空间 |
| **重排** | DashScope GTE-ReRank，失败退回向量排序 |
| **动态截断** | 阈值 0.35 + 最大保留 8 条，优先使用重排分数 |

**工具调用链路的 RAG**：`knowledge_search` 同样经过 扩大召回 → 向量检索 → 语义重排 → 相似度截断 → 格式化输出。

---

## 3. SearXNG 联网搜索接入

```
用户问题 → SearXNG(元搜索引擎,聚合bing/google等)
    → 搜索结果列表(URL + score)
    → SearchResultContentFetcherService(并发HTTP + Jsoup提取正文,超时7s)
    → 清洗过滤(空内容跳过,按score排序,限制topK)
    → 格式化注入prompt
```

**两种注入路径**：
- **NetworkSearchAdvisor**：搜索结果注入 user prompt，模型直接回答
- **SearchToolFacade.searchWeb**：工具调用返回格式化文本，注入 ReAct 轨迹

---

## 4. Docker 编排结构

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

**日志排错**：
```bash
docker compose logs -f backend          # 后端日志
docker compose logs backend | grep -i error  # 搜索错误
docker compose logs backend | grep -i "rerank\|react\|planner\|embedding\|searxng"
docker exec -it cv-backend sh           # 进入容器
docker compose restart backend          # 重启服务
```

---

## 5. 代码模块说明

### 后端（ai-robot-springboot）

| 模块 | 文件 | 职责 |
|------|------|------|
| Agent 编排 | AgentOrchestratorImpl.java | 统一编排 Foundation → Planner → ReAct/Fallback/Standard |
| Planner 决策 | AgentPlannerServiceImpl.java | 模型 JSON 决策，失败规则兜底 |
| 意图分类 | IntentClassifierStep.java | 关键词匹配识别意图 |
| RAG 装配 | ContextAssemblerStep.java | 调用 RAG 服务组装上下文 |
| Prompt 构建 | PromptBuilderStep.java | 构建 system/user prompt |
| 搜索工具门面 | SearchToolFacadeImpl.java | 知识库搜索(含重排) + 联网搜索 |
| 搜索工具 | SearchAgentTools.java | @Tool 注解暴露给模型 |
| RAG 服务 | ResumeKnowledgeRagServiceImpl.java | 双路检索 → 重排 → 截断 → 格式化 |
| 知识库服务 | ResumeKnowledgeBaseServiceImpl.java | CRUD + Embedding + 向量检索 |
| 重排服务 | DashScopeRerankServiceImpl.java | GTE-ReRank 调用 |
| 截断服务 | RagTruncationService.java | 相似度阈值截断 |
| 联网搜索 | SearXNGServiceImpl.java | SearXNG API 调用 |
| 内容抓取 | SearchResultContentFetcherServiceImpl.java | 并发抓取 + Jsoup 清洗 |
| ReAct 模型 | ReActStep.java | 多工具调用轨迹记录 |
| Agent 上下文 | AgentContext.java | 单次请求全量运行时上下文 |
| 审计日志 | AgentAuditLogger.java | 全链路耗时/状态记录 |

### 前端（ai-robot-vue3）

Vue 3 + Vite + Ant Design Vue + Pinia + Tailwind CSS + Markdown-it

### GitHub

[https://github.com/FMS-CODER/it-cv](https://github.com/FMS-CODER/it-cv)
