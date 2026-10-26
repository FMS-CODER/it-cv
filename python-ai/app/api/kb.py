"""知识库导入路由：DashScope 向量化后写入 cv.resume_knowledge_base。"""

import asyncio
import logging

from fastapi import APIRouter

from app.db import pg
from app.schemas.models import KbImportRequest
from app.services.embedding import embedding_service

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/ai/kb", tags=["knowledge-base"])


@router.post("/import")
async def import_knowledge(req: KbImportRequest):
    """批量导入：先整体向量化，再逐条入库；单条写入失败不影响其余条目。"""
    valid = [item for item in req.items if item.content and item.content.strip()]
    if not valid:
        return {"success": True, "data": {"imported": 0}}

    texts = [item.content.strip() for item in valid]
    try:
        vectors = await embedding_service.embed_texts(texts, text_type="document")
    except Exception as exc:
        logger.exception("知识库向量化失败")
        return {"success": False, "message": f"向量化失败: {exc}", "data": {"imported": 0}}

    imported = 0
    for item, vector in zip(valid, vectors):
        try:
            # 数据库为同步驱动，放线程池执行
            imported += await asyncio.to_thread(
                pg.insert_knowledge,
                item.content.strip(),
                item.category,
                item.metadata,
                vector,
            )
        except Exception as exc:
            logger.warning("知识库写入失败，已跳过该条: %s", exc)

    return {"success": True, "data": {"imported": imported}}
