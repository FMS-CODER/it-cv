"""DeepSeek 对话封装（OpenAI 兼容协议，基于 langchain-openai）。

对外提供：
- `astream`：异步流式输出正文片段，供 SSE 逐段推送；
- `acomplete`：一次性补全，供 Planner 决策等需要结构化输出的场景。
"""

import logging
from typing import AsyncGenerator, List, Optional

from langchain_core.messages import BaseMessage
from langchain_openai import ChatOpenAI

from app.config import get_settings

logger = logging.getLogger(__name__)


class LLMService:
    def __init__(self) -> None:
        self._settings = get_settings()
        self._llm: Optional[ChatOpenAI] = None

    def _get_llm(self) -> ChatOpenAI:
        if self._llm is None:
            if not self._settings.deepseek_api_key:
                raise RuntimeError("未配置 DEEPSEEK_API_KEY，无法调用对话模型")
            self._llm = ChatOpenAI(
                model=self._settings.chat_model,
                api_key=self._settings.deepseek_api_key,
                base_url=self._settings.deepseek_base,
                temperature=self._settings.chat_temperature,
                streaming=True,
                timeout=self._settings.http_timeout,
                max_retries=1,
            )
        return self._llm

    async def astream(self, messages: List[BaseMessage]) -> AsyncGenerator[str, None]:
        """流式返回正文文本片段（忽略空片段）。"""
        llm = self._get_llm()
        async for chunk in llm.astream(messages):
            text = chunk.content
            if isinstance(text, list):  # 部分实现会返回分段结构，这里做兼容
                text = "".join(
                    part.get("text", "") if isinstance(part, dict) else str(part) for part in text
                )
            if text:
                yield text

    async def acomplete(self, messages: List[BaseMessage]) -> str:
        """一次性补全，返回完整正文文本。"""
        llm = self._get_llm()
        resp = await llm.ainvoke(messages)
        content = resp.content
        if isinstance(content, list):
            content = "".join(
                part.get("text", "") if isinstance(part, dict) else str(part) for part in content
            )
        return content or ""


llm_service = LLMService()
