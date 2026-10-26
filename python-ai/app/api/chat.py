"""对话与简历优化的 SSE 流式路由。

SSE 数据格式对齐 Java 侧：每个事件为 JSON `{"v": "<片段>"}`，
前端沿用既有的解析逻辑即可无缝切换到本服务。
"""

import json
import logging

from fastapi import APIRouter
from sse_starlette.sse import EventSourceResponse

from app.schemas.models import ChatStreamRequest, ResumeOptimizeRequest
from app.services.agent import agent_orchestrator

logger = logging.getLogger(__name__)

router = APIRouter(tags=["chat"])


def _sse(chunk: str) -> dict:
    """封装单个 SSE 事件，确保中文不被转义。"""
    return {"data": json.dumps({"v": chunk}, ensure_ascii=False)}


@router.post("/ai/chat/stream")
async def chat_stream(req: ChatStreamRequest):
    """流式对话：Planner → 知识库/联网工具 → 流式生成。"""

    async def event_generator():
        try:
            async for chunk in agent_orchestrator.stream_chat(
                message=req.message,
                use_rag=req.useRag,
                use_web=req.useWeb,
                category=req.category,
                top_k=req.topK,
            ):
                yield _sse(chunk)
        except Exception as exc:
            logger.exception("流式对话异常")
            yield {"data": json.dumps({"error": str(exc)}, ensure_ascii=False)}

    return EventSourceResponse(event_generator())


@router.post("/ai/resume/optimize")
async def resume_optimize(req: ResumeOptimizeRequest):
    """流式简历优化：可选注入知识库 RAG 后流式产出建议。"""

    async def event_generator():
        try:
            async for chunk in agent_orchestrator.stream_resume_optimize(
                resume_text=req.resumeText,
                target_position=req.targetPosition,
                additional_requirements=req.additionalRequirements,
                knowledge_rag=req.knowledgeRag,
                category=req.kbCategory,
                top_k=req.kbTopK,
            ):
                yield _sse(chunk)
        except Exception as exc:
            logger.exception("简历优化流式异常")
            yield {"data": json.dumps({"error": str(exc)}, ensure_ascii=False)}

    return EventSourceResponse(event_generator())
