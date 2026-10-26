"""DashScope GTE-ReRank 重排封装。

关键约束（与 Java DashScopeRerankServiceImpl 一致）：
- 任何异常（HTTP 失败、解析失败、超时）都必须降级为「按向量相似度排序」，
  绝不能让重排问题中断整条 RAG 链路；
- 重排成功后以 rerank_score 作为 effectiveScore，截断阶段据此过滤。
"""

import logging
from typing import List, Optional

import httpx

from app.config import get_settings
from app.schemas.models import RankedDocument

logger = logging.getLogger(__name__)

# DashScope GTE-Rerank 官方端点（Java 侧使用简写 /api/v1/services/rerank，此处对齐官方完整路径）
_RERANK_PATH = "/api/v1/services/rerank/text-rerank/text-rerank"


class RerankService:
    def __init__(self) -> None:
        self._settings = get_settings()
        self._client: Optional[httpx.AsyncClient] = None

    def _get_client(self) -> httpx.AsyncClient:
        if self._client is None:
            self._client = httpx.AsyncClient(
                timeout=httpx.Timeout(self._settings.http_timeout, connect=5.0)
            )
        return self._client

    async def aclose(self) -> None:
        if self._client is not None:
            await self._client.aclose()
            self._client = None

    async def rerank(self, query: str, documents: List[RankedDocument]) -> List[RankedDocument]:
        """对候选文档重排；失败时降级为向量分数排序。"""
        if not documents or not query:
            return documents
        if not self._settings.rag_rerank_enabled:
            # 未启用重排：直接按向量分排序
            return self._fallback_to_vector_sort(documents)

        texts = [doc.content for doc in documents if doc.content]
        if not texts:
            return documents

        try:
            payload = {
                "model": self._settings.rag_rerank_model,
                "input": {"query": query, "documents": texts},
                "parameters": {"return_documents": False, "top_n": len(texts)},
            }
            headers = {
                "Authorization": f"Bearer {self._settings.dashscope_api_key}",
                "Content-Type": "application/json",
            }
            resp = await self._get_client().post(
                f"{self._settings.dashscope_base}{_RERANK_PATH}",
                headers=headers,
                json=payload,
            )
            if resp.status_code != 200:
                logger.warning("DashScope rerank 调用失败: status=%s", resp.status_code)
                return self._fallback_to_vector_sort(documents)
            return self._parse(resp.json(), documents)
        except Exception as exc:  # 超时 / 网络 / 解析等一律降级
            logger.warning("DashScope rerank 异常，退回向量排序: %s", exc)
            return self._fallback_to_vector_sort(documents)

    def _parse(self, data: dict, documents: List[RankedDocument]) -> List[RankedDocument]:
        """解析重排结果；字段缺失或结构异常同样降级。"""
        try:
            results = (data.get("output") or {}).get("results")
            if not isinstance(results, list):
                return self._fallback_to_vector_sort(documents)

            reranked: List[RankedDocument] = []
            for item in results:
                index = item.get("index", -1)
                # DashScope 返回 relevance_score，兼容部分版本返回 score
                score = item.get("relevance_score", item.get("score", 0.0))
                if isinstance(index, int) and 0 <= index < len(documents):
                    doc = documents[index]
                    doc.rerank_score = float(score)
                    reranked.append(doc)
            if not reranked:
                return self._fallback_to_vector_sort(documents)
            reranked.sort(key=lambda d: d.effective_score(), reverse=True)
            return reranked
        except Exception as exc:
            logger.warning("解析 rerank 响应失败: %s", exc)
            return self._fallback_to_vector_sort(documents)

    @staticmethod
    def _fallback_to_vector_sort(documents: List[RankedDocument]) -> List[RankedDocument]:
        documents.sort(key=lambda d: d.effective_score(), reverse=True)
        return documents


rerank_service = RerankService()
