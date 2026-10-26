"""DashScope text-embedding 封装（HTTP）。

对应 Java 侧 spring-ai-alibaba 的 DashScopeEmbeddingModel：
- 模型默认 text-embedding-v2，维度 1536；
- DashScope 单次最多 25 条文本，这里取 16 作为安全批量；
- 批量失败时逐条重试，尽量保证「输入条数 == 输出向量数」，维度不符则显式报错。
"""

import asyncio
import logging
from typing import List, Optional

import httpx

from app.config import get_settings

logger = logging.getLogger(__name__)

# DashScope text-embedding 单次最大 25 条，取 16 作为安全批量（与 Java 一致）
EMBED_BATCH_SIZE = 16


class EmbeddingError(RuntimeError):
    """embedding 调用失败（含超时、限流、维度不符）。"""


class EmbeddingService:
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

    @property
    def _endpoint(self) -> str:
        return f"{self._settings.dashscope_base}/api/v1/services/embeddings/text-embedding/text-embedding"

    def _headers(self) -> dict:
        if not self._settings.dashscope_api_key:
            raise EmbeddingError("未配置 DASHSCOPE_API_KEY，无法进行向量化")
        return {
            "Authorization": f"Bearer {self._settings.dashscope_api_key}",
            "Content-Type": "application/json",
        }

    async def _call(self, texts: List[str], text_type: str) -> List[List[float]]:
        """单次调用 DashScope embedding 接口，返回与输入等长的向量列表。"""
        payload = {
            "model": self._settings.embedding_model,
            "input": {"texts": texts},
            # query 类型用于检索问题，document 类型用于入库文本（DashScope 建议区分）
            "parameters": {"text_type": text_type},
        }
        resp = await self._get_client().post(self._endpoint, headers=self._headers(), json=payload)
        if resp.status_code != 200:
            raise EmbeddingError(f"DashScope embedding HTTP {resp.status_code}: {resp.text[:200]}")
        data = resp.json()
        embeddings = (data.get("output") or {}).get("embeddings") or []
        if len(embeddings) != len(texts):
            raise EmbeddingError(
                f"embedding 返回数量不匹配: 输入 {len(texts)} 条，返回 {len(embeddings)} 条"
            )
        # 按 text_index 排序，避免返回顺序错乱导致内容与向量错位
        embeddings.sort(key=lambda x: x.get("text_index", 0))
        vectors = [item.get("embedding") or [] for item in embeddings]
        for i, vec in enumerate(vectors):
            self._validate_dim(vec, index=i)
        return vectors

    def _validate_dim(self, vector: List[float], index: int) -> None:
        """校验维度：数据库 embedding 列是 vector(1536)，维度不符写入必然失败，故提前拦截。"""
        expected = self._settings.embedding_dim
        if len(vector) != expected:
            raise EmbeddingError(
                f"第 {index + 1} 条 embedding 维度不符: 期望 {expected}，实际 {len(vector)}"
            )

    async def embed_texts(self, texts: List[str], text_type: str = "document") -> List[List[float]]:
        """批量向量化，返回与输入等长（且顺序一致）的向量列表。

        批量失败时逐条重试；若单条仍失败则抛出异常（调用方决定是否降级）。
        """
        if not texts:
            return []
        results: List[List[float]] = []
        for start in range(0, len(texts), EMBED_BATCH_SIZE):
            batch = texts[start: start + EMBED_BATCH_SIZE]
            try:
                results.extend(await self._call(batch, text_type))
            except EmbeddingError as exc:
                logger.warning("批量 embedding 失败，逐条重试: %s", exc)
                for text in batch:
                    results.extend(await self._call([text], text_type))
        return results

    async def embed_query(self, text: str) -> List[float]:
        """检索问题向量化（text_type=query），与入库时区分以提升召回质量。"""
        vectors = await self.embed_texts([text], text_type="query")
        return vectors[0]


# 模块级单例，供其他 service / api 复用
embedding_service = EmbeddingService()
