"""RAG 双路检索 + 合并去重 + 重排 + 阈值截断。

严格对齐 Java ResumeKnowledgeRagServiceImpl 的逻辑：
1. 双路查询：q1 = 原始语义；q2 = 原始语义 + 「输出锚点」（引导偏向回答/优化方向）；
2. 两路各自召回 perQuery 条，按 id 去重合并（q1 优先）；
3. 放大召回：expandK = topK × expandFactor，perQuery 取 expandK 的一半（至少 2）；
4. 重排：启用时走 gte-rerank，失败自动降级为向量分排序；
5. 截断：按相似度阈值（默认 0.35）过滤 + 最大条数（默认 8）截断。
"""

import asyncio
import logging
from typing import List, Optional

from app.config import get_settings
from app.db import pg
from app.schemas.models import RankedDocument
from app.services.embedding import embedding_service
from app.services.rerank import rerank_service

logger = logging.getLogger(__name__)

# 输出锚点：给检索问题补一个「回答方向」提示，使召回更贴近生成侧需求
OUTPUT_ANCHOR_CHAT = "\n\n（请从简历撰写、面试表达、职业发展、量化成果等角度补充相关条目）"
OUTPUT_ANCHOR_RESUME = "\n\n（请从简历结构、STAR、项目描述、技能关键词、面试话术等角度补充相关条目）"

# 简历正文进入检索时截断长度（与 Java 一致）
_RESUME_MAX_LEN = 2000


class RetrievalService:
    def __init__(self) -> None:
        self._settings = get_settings()

    # ------------------------------------------------------------------ #
    # 对外：对话场景检索
    # ------------------------------------------------------------------ #
    async def search(
        self,
        query: str,
        top_k: int = 5,
        category: Optional[str] = None,
    ) -> List[RankedDocument]:
        """对话场景：q1=用户输入，q2=用户输入+对话锚点。"""
        q = (query or "").strip()
        if not q:
            return []
        k = top_k if top_k and top_k > 0 else 5
        return await self.retrieve(q, q + OUTPUT_ANCHOR_CHAT, category, k)

    # ------------------------------------------------------------------ #
    # 对外：简历优化场景检索
    # ------------------------------------------------------------------ #
    async def search_resume(
        self,
        target_position: str,
        resume_text: str,
        additional_requirements: str,
        top_k: int = 5,
        category: Optional[str] = None,
    ) -> List[RankedDocument]:
        """简历优化场景：q1=岗位+简历正文；q2=岗位+额外要求+简历锚点。"""
        pos = (target_position or "").strip()
        resume = self._truncate(resume_text or "", _RESUME_MAX_LEN)
        extra = (additional_requirements or "").strip()

        q1 = f"{pos}\n\n{resume}"
        q2 = pos + ("" if not extra else f"\n额外要求：{extra}") + OUTPUT_ANCHOR_RESUME
        k = top_k if top_k and top_k > 0 else 5
        return await self.retrieve(q1, q2, category, k)

    # ------------------------------------------------------------------ #
    # 核心：双路检索 → 重排 → 截断
    # ------------------------------------------------------------------ #
    async def retrieve(
        self,
        query1: str,
        query2: str,
        category: Optional[str],
        top_k: int,
    ) -> List[RankedDocument]:
        expand_k = top_k * max(1, self._settings.rag_expand_factor)
        # 每路召回量：放大后总量的一半（向上取整），至少 2 条，保证去重后仍有候选
        per_query = max(2, (expand_k + 1) // 2)

        candidates = await self._merge_dual_search(query1, query2, category, per_query)
        if not candidates:
            return []

        if self._settings.rag_rerank_enabled:
            candidates = await rerank_service.rerank(query1, candidates)
        else:
            candidates.sort(key=lambda d: d.effective_score(), reverse=True)

        return self.truncate_by_similarity(candidates)

    async def _merge_dual_search(
        self,
        query1: str,
        query2: str,
        category: Optional[str],
        per_query: int,
    ) -> List[RankedDocument]:
        """并发执行两路检索，按 id 去重合并（q1 结果优先）。"""
        results_a, results_b = await asyncio.gather(
            self._search_one(query1, category, per_query),
            self._search_one(query2, category, per_query),
        )
        merged: dict[int, RankedDocument] = {}
        for doc in results_a:  # 先放 q1（原始语义），保持其优先级
            if doc.id is not None and doc.id not in merged:
                merged[doc.id] = doc
        for doc in results_b:
            if doc.id is not None and doc.id not in merged:
                merged[doc.id] = doc
        return list(merged.values())

    async def _search_one(
        self,
        query: str,
        category: Optional[str],
        top_k: int,
    ) -> List[RankedDocument]:
        """单路检索：向量化 query 后走 pgvector 余弦检索。失败返回空列表，不抛异常。"""
        try:
            vector = await embedding_service.embed_query(query)
        except Exception as exc:
            logger.warning("query 向量化失败: %s", exc)
            return []

        # 数据库为同步驱动，放到线程池执行避免阻塞事件循环
        try:
            rows = await asyncio.to_thread(
                pg.search_similar, vector, top_k, (category or None)
            )
        except Exception as exc:
            logger.warning("相似度检索失败: %s", exc)
            return []

        docs: List[RankedDocument] = []
        for row in rows:
            docs.append(
                RankedDocument(
                    id=row.get("id"),
                    content=row.get("content") or "",
                    category=row.get("category"),
                    vector_score=float(row["similarity"]) if row.get("similarity") is not None else None,
                )
            )
        return docs

    # ------------------------------------------------------------------ #
    # 截断（阈值 + 最大条数）
    # ------------------------------------------------------------------ #
    def truncate_by_similarity(self, documents: List[RankedDocument]) -> List[RankedDocument]:
        """按有效分数阈值过滤，并限制最大条数。入参需已按分数降序。"""
        if not documents:
            return documents
        threshold = self._settings.rag_similarity_threshold
        max_k = self._settings.rag_max_top_k
        kept: List[RankedDocument] = []
        for doc in documents:
            if len(kept) >= max_k:
                break
            if doc.effective_score() < threshold:
                continue
            kept.append(doc)
        return kept

    # ------------------------------------------------------------------ #
    # 上下文格式化（供 prompt 注入）
    # ------------------------------------------------------------------ #
    @staticmethod
    def format_context(scene: str, items: List[RankedDocument]) -> str:
        """对齐 Java formatContext，生成注入 LLM 的知识库片段文本。"""
        if not items:
            return ""
        lines: List[str] = [
            f"## 【知识库 RAG · {scene}】",
            "以下为从向量知识库检索到的参考片段（按相关性排序）。"
            "请优先据此组织回答，必要时再补充通用经验；勿虚构知识库中不存在的事实。",
            "",
        ]
        index = 1
        for doc in items:
            if not doc.content.strip():
                continue
            sim = doc.effective_score()
            lines.append(
                f"### 片段 {index}（相关性 {sim:.3f}，分类：{doc.category or ''}）"
            )
            lines.append(doc.content.strip())
            lines.append("")
            index += 1
        return "\n".join(lines).strip()

    @staticmethod
    def _truncate(text: str, max_len: int) -> str:
        if text is None:
            return ""
        if len(text) <= max_len:
            return text
        return text[:max_len] + "\n…（已截断）"


retrieval_service = RetrievalService()
