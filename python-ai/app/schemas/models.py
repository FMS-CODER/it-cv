"""Pydantic 请求 / 响应模型，以及内部检索文档模型。"""

from dataclasses import dataclass
from typing import Any, List, Optional

from pydantic import BaseModel, Field


# =============================================================================
# 内部领域模型（非对外接口，仅服务内部流转）
# =============================================================================
@dataclass
class RankedDocument:
    """检索候选文档：同时保留向量分数与重排分数。

    对应 Java 的 RankedDocument：重排成功后以 rerank_score 为准，
    重排失败/未启用时退化为 vector_score，保证排序链路始终可用。
    """

    id: int
    content: str
    category: Optional[str] = None
    vector_score: Optional[float] = None
    rerank_score: Optional[float] = None

    def effective_score(self) -> float:
        """有效分数：优先重排分，其次向量分，均无则为 0。"""
        if self.rerank_score is not None:
            return self.rerank_score
        if self.vector_score is not None:
            return self.vector_score
        return 0.0


# =============================================================================
# 检索接口
# =============================================================================
class RagSearchRequest(BaseModel):
    """/ai/rag/search 请求体。"""

    query: str = Field(..., description="检索问题")
    topK: int = Field(5, ge=1, le=50, description="期望返回条数，默认 5")
    category: Optional[str] = Field(None, description="按分类过滤，空=全部")


class RagSearchItem(BaseModel):
    id: int
    content: str
    category: Optional[str] = None
    similarity: float


# =============================================================================
# 知识库导入接口
# =============================================================================
class KbImportItem(BaseModel):
    content: str = Field(..., description="知识内容")
    category: Optional[str] = Field(None, description="分类，默认「默认」")
    # metadata 允许传 JSON 字符串或对象
    metadata: Optional[Any] = Field(None, description="结构化元数据，JSON 字符串或对象均可")


class KbImportRequest(BaseModel):
    items: List[KbImportItem] = Field(default_factory=list)


# =============================================================================
# 对话 / 简历优化
# =============================================================================
class ChatStreamRequest(BaseModel):
    """/ai/chat/stream 请求体（对齐 Java AiChatReqVO 的核心字段）。"""

    message: str = Field(..., description="用户输入")
    chatId: Optional[str] = None
    useRag: bool = Field(True, description="是否启用知识库 RAG")
    useWeb: bool = Field(False, description="是否启用联网搜索（可选）")
    category: Optional[str] = Field(None, description="知识库分类过滤")
    topK: int = Field(5, ge=1, le=50, description="知识库检索 Top-K")


class ResumeOptimizeRequest(BaseModel):
    """/ai/resume/optimize 请求体。"""

    resumeText: str = Field("", description="简历原文")
    targetPosition: str = Field("", description="目标岗位")
    additionalRequirements: str = Field("", description="额外要求")
    knowledgeRag: bool = Field(True, description="是否启用知识库 RAG")
    kbCategory: Optional[str] = Field(None, description="知识库分类过滤")
    kbTopK: int = Field(5, ge=1, le=50, description="知识库检索 Top-K")


# =============================================================================
# 通用响应包装（对齐 Java Response：{success, data, message}）
# =============================================================================
class HealthResponse(BaseModel):
    status: str
    service: str
    version: str
