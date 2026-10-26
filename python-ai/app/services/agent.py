"""简化版 Agent 编排：Planner → 工具调用 → 生成。

对齐 Java AgentOrchestrator 的主干思路，但做了简化（不还原 ReAct 多轮循环、
不做对话落库与审计日志）：
1. Planner：由 LLM 判断是否需要查知识库 / 联网，并给出工具查询词；失败则用默认策略；
2. 工具调用：按计划并发执行知识库检索（RAG）与联网搜索；
3. 生成：把工具结果注入 system prompt，流式产出最终回答。
"""

import asyncio
import json
import logging
import re
from typing import AsyncGenerator, Optional

from langchain_core.messages import HumanMessage, SystemMessage

from app.services.llm import llm_service
from app.services.retrieval import retrieval_service
from app.services.search import search_service

logger = logging.getLogger(__name__)

# 对话场景的基础人设
_CHAT_PERSONA = (
    "你是「智询工作台」的 AI 助手，擅长简历优化、面试辅导与职业发展咨询。"
    "回答要专业、具体、可执行，能给出量化建议时请给出。"
)

# 简历优化场景的基础人设
_RESUME_PERSONA = (
    "你是资深简历优化顾问，熟悉互联网/IT 岗位招聘标准。"
    "请基于用户简历与目标岗位，给出结构化、可落地的优化建议，"
    "包含：整体评价、逐模块修改建议（可给出改写示例）、关键词补充、面试话术提示。"
)

_PLANNER_PROMPT = (
    "你是检索规划器。请判断回答下面的用户问题需要用到哪些工具，"
    "并给出最合适的检索查询词。\n"
    "只输出 JSON，不要输出任何解释，格式：\n"
    '{"need_knowledge": true/false, "need_web": true/false, "tool_query": "检索词"}\n'
    "说明：need_knowledge=查询内部简历知识库；need_web=联网搜索最新信息；"
    "tool_query 是用于检索的精炼查询词（可与原问题一致）。\n"
    "用户问题："
)


class AgentOrchestrator:
    def __init__(self) -> None:
        self._retrieval = retrieval_service
        self._llm = llm_service
        self._search = search_service

    # ------------------------------------------------------------------ #
    # Planner
    # ------------------------------------------------------------------ #
    async def _plan(self, message: str, default_knowledge: bool, default_web: bool) -> dict:
        """LLM 规划；任何异常都回退到默认策略，保证链路可用。"""
        default = {
            "need_knowledge": default_knowledge,
            "need_web": default_web,
            "tool_query": message,
        }
        try:
            text = await self._llm.acomplete(
                [HumanMessage(content=_PLANNER_PROMPT + (message or ""))]
            )
            decision = self._parse_json(text)
            if not isinstance(decision, dict):
                return default
            return {
                "need_knowledge": bool(decision.get("need_knowledge", default_knowledge)),
                "need_web": bool(decision.get("need_web", default_web)),
                "tool_query": (decision.get("tool_query") or message or "").strip() or message,
            }
        except Exception as exc:
            logger.warning("Planner 规划失败，使用默认策略: %s", exc)
            return default

    @staticmethod
    def _parse_json(text: str) -> Optional[dict]:
        if not text:
            return None
        match = re.search(r"\{.*\}", text, re.DOTALL)
        if not match:
            return None
        try:
            return json.loads(match.group(0))
        except json.JSONDecodeError:
            return None

    # ------------------------------------------------------------------ #
    # 对话流式生成
    # ------------------------------------------------------------------ #
    async def stream_chat(
        self,
        message: str,
        use_rag: bool = True,
        use_web: bool = False,
        category: Optional[str] = None,
        top_k: int = 5,
    ) -> AsyncGenerator[str, None]:
        """Planner → 工具调用 → 流式生成。"""
        plan = await self._plan(message, use_rag, use_web)
        need_knowledge = use_rag or plan["need_knowledge"]
        need_web = use_web or plan["need_web"]
        query = plan["tool_query"] or message

        rag_context, web_context = await self._run_tools(need_knowledge, need_web, query, category, top_k)

        system = self._compose_system(_CHAT_PERSONA, rag_context, web_context)
        messages = [SystemMessage(content=system), HumanMessage(content=message)]
        async for chunk in self._llm.astream(messages):
            yield chunk

    # ------------------------------------------------------------------ #
    # 简历优化流式生成
    # ------------------------------------------------------------------ #
    async def stream_resume_optimize(
        self,
        resume_text: str,
        target_position: str,
        additional_requirements: str,
        knowledge_rag: bool = True,
        category: Optional[str] = None,
        top_k: int = 5,
    ) -> AsyncGenerator[str, None]:
        """简历优化：检索知识库后流式产出建议（对齐 Java 的 resume-direct 模式）。"""
        rag_context = ""
        if knowledge_rag:
            try:
                docs = await self._retrieval.search_resume(
                    target_position, resume_text, additional_requirements, top_k, category
                )
                rag_context = self._retrieval.format_context("简历优化", docs)
            except Exception as exc:
                logger.warning("简历优化 RAG 检索失败，跳过知识库注入: %s", exc)

        system = self._compose_system(_RESUME_PERSONA, rag_context, "")
        user = (
            f"## 目标岗位\n{target_position or '（未填写）'}\n\n"
            f"## 额外要求\n{additional_requirements or '（无）'}\n\n"
            f"## 简历原文\n{resume_text or '（未填写）'}\n\n"
            "请给出上述简历的优化建议。"
        )
        messages = [SystemMessage(content=system), HumanMessage(content=user)]
        async for chunk in self._llm.astream(messages):
            yield chunk

    # ------------------------------------------------------------------ #
    # 工具调用
    # ------------------------------------------------------------------ #
    async def _run_tools(
        self,
        need_knowledge: bool,
        need_web: bool,
        query: str,
        category: Optional[str],
        top_k: int,
    ):
        """按计划并发执行工具；单个工具失败不影响另一个。"""
        rag_task = self._safe_knowledge(query, category, top_k) if need_knowledge else None
        web_task = self._safe_web(query, min(top_k, 5)) if need_web else None

        rag_context, web_context = "", ""
        tasks = [t for t in (rag_task, web_task) if t is not None]
        if not tasks:
            return rag_context, web_context

        results = await asyncio.gather(*tasks, return_exceptions=True)
        idx = 0
        if rag_task is not None:
            rag_context = results[idx] if not isinstance(results[idx], Exception) else ""
            idx += 1
        if web_task is not None:
            web_context = results[idx] if not isinstance(results[idx], Exception) else ""
        return rag_context, web_context

    async def _safe_knowledge(self, query: str, category: Optional[str], top_k: int) -> str:
        try:
            docs = await self._retrieval.search(query, top_k, category)
            return self._retrieval.format_context("对话", docs)
        except Exception as exc:
            logger.warning("知识库工具调用失败: %s", exc)
            return ""

    async def _safe_web(self, query: str, top_k: int) -> str:
        try:
            return await self._search.build_context(query, top_k)
        except Exception as exc:
            logger.warning("联网搜索工具调用失败: %s", exc)
            return ""

    @staticmethod
    def _compose_system(persona: str, rag_context: str, web_context: str) -> str:
        parts = [persona]
        if rag_context:
            parts.append(rag_context)
        if web_context:
            parts.append(web_context)
        return "\n\n".join(parts)


agent_orchestrator = AgentOrchestrator()
