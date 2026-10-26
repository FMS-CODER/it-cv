"""FastAPI 应用入口：注册路由、生命周期管理与健康检查。"""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app import __version__
from app.api import chat, kb, rag
from app.db import pg
from app.schemas.models import HealthResponse
from app.services.embedding import embedding_service
from app.services.rerank import rerank_service
from app.services.search import search_service

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(_: FastAPI):
    """启动时初始化资源，退出时释放；单个依赖不可用不阻断服务启动。"""
    pg.open_pool()
    try:
        pg.ensure_embedding_column()
    except Exception as exc:  # 数据库不可用时不阻断服务
        logger.warning("初始化 embedding 列元数据失败: %s", exc)
    logger.info("Python AI 服务已启动")
    try:
        yield
    finally:
        pg.close_pool()
        await embedding_service.aclose()
        await rerank_service.aclose()
        await search_service.aclose()
        logger.info("Python AI 服务已关闭")


app = FastAPI(
    title="智询工作台 Python AI 服务",
    description="RAG 检索 / 知识库导入 / 流式对话 / 简历优化",
    version=__version__,
    lifespan=lifespan,
)

# 允许前端(5173/5174)与后续 Spring Boot 调用
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(rag.router)
app.include_router(kb.router)
app.include_router(chat.router)


@app.get("/health", response_model=HealthResponse, tags=["system"])
async def health() -> HealthResponse:
    """健康检查。"""
    return HealthResponse(status="ok", service="python-ai", version=__version__)
