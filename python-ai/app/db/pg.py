"""PostgreSQL(pgvector) 访问层：连接池、embedding 列元数据探测、向量写入与余弦检索。

选型说明（为什么用 psycopg 手写 SQL 而非向量库 SDK）：
1. 与 Java 实现保持一致——Java 侧同样手工拼 SQL 并显式 `::vector` cast，
   直接拼 SQL 可完全对齐其检索语义（余弦距离公式、category 过滤、动态 cast）；
2. pgvector 的 SQL 语法足够简单（`<=>` 余弦距离、`::vector` 强转），引入 ORM/SDK
   反而增加映射层，出错时更难定位维度/类型问题。
"""

import json
import logging
import threading
from typing import Any, Dict, List, Optional

from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool

from app.config import get_settings

logger = logging.getLogger(__name__)

# 表名（schema 固定为 cv，与 Java currentSchema=cv 对齐）
_KB_TABLE = "cv.resume_knowledge_base"

_pool: Optional[ConnectionPool] = None
_pool_lock = threading.Lock()

# embedding 列元数据缓存：类型分类（vector/text）、维度、用于 cast 的类型全名
_embedding_meta: Dict[str, Any] = {"category": None, "dim": 0, "cast": "vector"}
_meta_lock = threading.Lock()


def _normalize_conninfo(url: str) -> str:
    """兼容 SQLAlchemy 风格的 DSN（去掉 +psycopg 等驱动后缀）。"""
    for prefix in ("postgresql+psycopg://", "postgresql+asyncpg://", "postgres://"):
        if url.startswith(prefix):
            return "postgresql://" + url[len(prefix):]
    return url


def _configure_connection(conn: Any) -> None:
    """每个新连接建立时设置 search_path，保证 vector 类型与 cv 表可见。"""
    conn.execute("SET search_path TO cv, public")


def get_pool() -> ConnectionPool:
    """懒加载连接池单例（不在此处建立实际连接）。"""
    global _pool
    if _pool is None:
        with _pool_lock:
            if _pool is None:
                settings = get_settings()
                conninfo = _normalize_conninfo(settings.database_url)
                _pool = ConnectionPool(
                    conninfo=conninfo,
                    min_size=settings.db_pool_min_size,
                    max_size=settings.db_pool_max_size,
                    open=False,
                    configure=_configure_connection,
                )
    return _pool


def open_pool() -> None:
    """启动时尝试打开连接池；数据库不可用只告警，不阻断服务启动。"""
    try:
        get_pool().open(wait=False)
        logger.info("PostgreSQL 连接池已初始化")
    except Exception as exc:  # pragma: no cover - 环境相关
        logger.warning("PostgreSQL 连接池初始化失败（服务仍可启动，DB 接口将返回失败）: %s", exc)


def close_pool() -> None:
    global _pool
    if _pool is not None:
        try:
            _pool.close()
        except Exception as exc:  # pragma: no cover
            logger.warning("关闭 PostgreSQL 连接池异常: %s", exc)
        _pool = None


# =============================================================================
# embedding 列元数据探测（为什么需要：表可能未启用 pgvector 或维度未知，
# 需要在 SQL cast 时使用正确类型，避免硬编码 vector(1536) 导致报错）
# =============================================================================
def ensure_embedding_column() -> Dict[str, Any]:
    """查询 pg_attribute 得到 embedding 列的真实类型，缓存结果。"""
    if _embedding_meta["category"] is not None:
        return _embedding_meta
    with _meta_lock:
        if _embedding_meta["category"] is not None:
            return _embedding_meta
        try:
            with get_pool().connection() as conn:
                with conn.cursor() as cur:
                    cur.execute(
                        """
                        SELECT pg_catalog.format_type(a.atttypid, a.atttypmod) AS data_type
                        FROM pg_attribute a
                        JOIN pg_class c ON c.oid = a.attrelid
                        WHERE c.relname = 'resume_knowledge_base'
                          AND a.attname = 'embedding'
                          AND a.attnum > 0
                        """
                    )
                    row = cur.fetchone()
            if row is None:
                logger.warning("未找到 resume_knowledge_base.embedding 列，请确认表结构")
                return _embedding_meta
            col_type = str(row[0]).strip()
            _embedding_meta["cast"] = col_type
            if "vector" in col_type:
                _embedding_meta["category"] = "vector"
                if "(" in col_type and ")" in col_type:
                    inner = col_type[col_type.index("(") + 1: col_type.index(")")].strip()
                    try:
                        _embedding_meta["dim"] = int(inner)
                    except ValueError:
                        pass
                logger.info("检测到 embedding 列类型: %s（维度 %s）", col_type, _embedding_meta["dim"])
            else:
                _embedding_meta["category"] = "text"
                logger.info("embedding 列为 %s，未使用 pgvector", col_type)
        except Exception as exc:  # pragma: no cover
            logger.warning("读取 embedding 列元数据失败: %s", exc)
    return _embedding_meta


def cast_embedding_type() -> str:
    """返回 SQL cast 使用的类型名（优先使用 pg 元数据中的全名）。"""
    meta = ensure_embedding_column()
    if meta["cast"]:
        return meta["cast"]
    if meta["category"] == "vector" and meta["dim"] > 0:
        return f"vector({meta['dim']})"
    return "vector"


# =============================================================================
# 向量工具
# =============================================================================
def to_vector_str(vector: Optional[List[float]]) -> Optional[str]:
    """float 列表转 pgvector 字符串 `[0.1,0.2,...]`；空值返回 None。"""
    if not vector:
        return None
    return "[" + ",".join(repr(float(x)) for x in vector) + "]"


def _to_jsonb(metadata: Any) -> Optional[str]:
    """metadata 归一化为 jsonb 字符串：dict/list 直接序列化，字符串原样返回。"""
    if metadata is None:
        return None
    if isinstance(metadata, str):
        text = metadata.strip()
        return text or None
    return json.dumps(metadata, ensure_ascii=False)


# =============================================================================
# 写入
# =============================================================================
def insert_knowledge(
    content: str,
    category: Optional[str],
    metadata: Any,
    embedding: Optional[List[float]],
) -> int:
    """插入单条知识，返回受影响行数。embedding 为 None 时写入 NULL。"""
    cast = cast_embedding_type()
    vec_str = to_vector_str(embedding)
    with get_pool().connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                f"""
                INSERT INTO {_KB_TABLE} (content, category, metadata, embedding)
                VALUES (%s, %s, %s::jsonb, %s::{cast})
                """,
                (content, category or "默认", _to_jsonb(metadata), vec_str),
            )
            rowcount = cur.rowcount
        conn.commit()
    return rowcount


# =============================================================================
# 检索
# =============================================================================
def search_similar(
    query_vector: List[float],
    top_k: int,
    category: Optional[str] = None,
) -> List[Dict[str, Any]]:
    """pgvector 余弦相似检索。

    相似度公式刻意与 Java 保持一致：`1 - (embedding <=> q) / 2`。
    pgvector 的 `<=>` 为余弦距离（范围 [0,2]），除以 2 后再取补，
    将其归一化到 [0,1]，与 Java 的相似度阈值（0.35）语义对齐。
    """
    cast = cast_embedding_type()
    vec_str = to_vector_str(query_vector)
    if vec_str is None:
        return []

    sql = (
        "SELECT id, content, category, metadata::text AS metadata, "
        f"       1 - (embedding <=> %(qv)s::{cast}) / 2 AS similarity "
        f"FROM {_KB_TABLE} "
        "WHERE embedding IS NOT NULL"
    )
    params: Dict[str, Any] = {"qv": vec_str, "limit": top_k}
    if category:
        sql += " AND category = %(category)s"
        params["category"] = category
    sql += f" ORDER BY embedding <=> %(qv)s::{cast} ASC LIMIT %(limit)s"

    with get_pool().connection() as conn:
        with conn.cursor(row_factory=dict_row) as cur:
            cur.execute(sql, params)
            rows = cur.fetchall()
    return [dict(r) for r in rows]
