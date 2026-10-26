"""知识库检索路由。"""

import logging

from fastapi import APIRouter

from app.schemas.models import RagSearchRequest
from app.services.retrieval import retrieval_service

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/ai/rag", tags=["rag"])


@router.post("/search")
async def search(req: RagSearchRequest):
    """知识库向量检索：复刻 Java 的双路检索 + 重排 + 阈值截断。"""
    try:
        docs = await retrieval_service.search(req.query, req.topK, req.category)
        data = [
            {
                "id": doc.id,
                "content": doc.content,
                "category": doc.category,
                "similarity": round(doc.effective_score(), 4),
            }
            for doc in docs
        ]
        return {"success": True, "data": data}
    except Exception as exc:
        logger.exception("知识库检索失败")
        return {"success": False, "message": str(exc), "data": []}
