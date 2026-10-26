"""SearXNG 联网搜索封装（可选能力）。

对应 Java 的 SearXNGServiceImpl + SearchResultContentFetcherServiceImpl：
- 调 SearXNG JSON 接口，按 score 降序取前 N 条；
- 可选并发抓取结果页正文并抽取纯文本；
- 任何失败（服务未启动、超时、解析异常）都必须兜底返回空，绝不上抛。
"""

import asyncio
import html
import logging
from html.parser import HTMLParser
from typing import Any, Dict, List, Optional

import httpx

from app.config import get_settings

logger = logging.getLogger(__name__)

# 与 Java 保持一致的聚合搜索引擎列表（按本地网络可达性挑选）
_SEARXNG_ENGINES = (
    "wolframalpha,presearch,seznam,mwmbl,encyclosearch,bpb,mojeek,"
    "right dao,wikimini,crowdview,searchmysite,bing,naver,360search"
)

_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"


class _TextExtractor(HTMLParser):
    """极简 HTML 正文抽取器：跳过 script/style，拼接可见文本。"""

    def __init__(self) -> None:
        super().__init__()
        self._skip = 0
        self._parts: List[str] = []

    def handle_starttag(self, tag: str, attrs: Any) -> None:
        if tag in ("script", "style", "noscript"):
            self._skip += 1

    def handle_endtag(self, tag: str) -> None:
        if tag in ("script", "style", "noscript") and self._skip > 0:
            self._skip -= 1

    def handle_data(self, data: str) -> None:
        if self._skip == 0 and data and data.strip():
            self._parts.append(data.strip())

    def text(self) -> str:
        return " ".join(self._parts)


class SearchService:
    def __init__(self) -> None:
        self._settings = get_settings()
        self._client: Optional[httpx.AsyncClient] = None

    def _get_client(self) -> httpx.AsyncClient:
        if self._client is None:
            self._client = httpx.AsyncClient(
                timeout=httpx.Timeout(self._settings.http_timeout, connect=5.0),
                headers={"User-Agent": _UA},
            )
        return self._client

    async def aclose(self) -> None:
        if self._client is not None:
            await self._client.aclose()
            self._client = None

    async def search(self, query: str, count: int = 5) -> List[Dict[str, Any]]:
        """调用 SearXNG，返回 [{url, title, score}]，失败返回空列表。"""
        if not query:
            return []
        params = {"q": query, "format": "json", "engines": _SEARXNG_ENGINES}
        try:
            resp = await self._get_client().get(self._settings.searxng_url, params=params)
            if resp.status_code != 200:
                logger.warning("SearXNG 搜索失败: status=%s", resp.status_code)
                return []
            results = (resp.json() or {}).get("results") or []
            ranked = sorted(results, key=lambda r: r.get("score", 0.0), reverse=True)[:count]
            return [
                {
                    "url": r.get("url", ""),
                    "title": r.get("title", ""),
                    "score": float(r.get("score", 0.0)),
                }
                for r in ranked
            ]
        except Exception as exc:  # 服务不可用 / 超时 / 非 JSON，一律兜底
            logger.warning("SearXNG 搜索异常，返回空结果: %s", exc)
            return []

    async def _fetch_text(self, url: str) -> str:
        """抓取页面并抽取纯文本，失败返回空字符串。"""
        try:
            resp = await self._get_client().get(url, headers={"Accept": "text/html"})
            if resp.status_code != 200:
                return ""
            parser = _TextExtractor()
            parser.feed(resp.text)
            return html.unescape(parser.text())
        except Exception:
            return ""

    async def build_context(self, query: str, max_results: int = 5) -> str:
        """搜索并抓取正文，拼装为可注入 prompt 的文本；无结果时返回空串。"""
        results = await self.search(query, max_results)
        if not results:
            return ""
        texts = await asyncio.gather(*(self._fetch_text(r["url"]) for r in results))
        lines: List[str] = ["## 【联网搜索结果】"]
        for item, text in zip(results, texts):
            snippet = (text or "")[:800]
            lines.append(f"### {item['title'] or item['url']}")
            lines.append(f"来源：{item['url']}")
            if snippet:
                lines.append(snippet)
            lines.append("")
        return "\n".join(lines).strip()


search_service = SearchService()
