# Agent 指标验证说明

## 能力范围
- 统一 Agent 编排基线查询：`/agent-metrics/baseline`
- 最近运行指标：`/agent-metrics/runs/recent`
- RAG 评测：`/agent-metrics/benchmark/rag`
- 联网搜索评测：`/agent-metrics/benchmark/web`
- SSE 对话评测：`/agent-metrics/benchmark/chat`
- 指标报告与简历条目：`/agent-metrics/report`

## 指标口径
- `首包时间`：请求开始到首个 SSE 事件输出的耗时。
- `完整响应时间`：请求开始到流式输出结束的总耗时。
- `降级成功率`：触发 Tool Calling 降级后，最终成功返回有效内容的请求占比。
- `RAG 关键词覆盖率`：离线样本中，检索上下文命中预期关键词的比例。
- `近似 Token 压缩率`：`1 - 清洗后近似 Token / 原始网页近似 Token`。

## 推荐压测流程
1. 先打开前端“指标验证”页，点击“刷新基线”确认数据库、pgvector、SearXNG 和统一编排链路已就绪。
2. 执行 `RAG 评测`，观察双路召回平均耗时、P95 和关键词覆盖提升。
3. 执行 `联网搜索评测`，观察搜索耗时、抓取耗时与近似 Token 压缩率。
4. 执行 `SSE 对话评测`，记录首包均值、完整响应均值和降级成功率。
5. 点击“生成报告与简历条目”，将结果整理进项目经历。

## 简历写法建议
- 只写已经测出来的数据，不写拍脑袋的 `[xx%]`。
- 尽量写均值、P95、提升比例，不写单次最优值。
- 如果 Token 使用的是近似估算，要在面试里明确说明是“清洗前后对比口径”。
