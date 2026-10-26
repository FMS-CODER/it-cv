"""全局配置：统一从环境变量 / .env 读取，严禁在代码中硬编码密钥。"""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """服务配置项，字段名与 .env.example 中的环境变量一一对应（大小写不敏感）。"""

    model_config = SettingsConfigDict(
        env_file=".env",          # 本地开发读取 .env，生产直接用环境变量
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",           # 忽略无关环境变量，避免误报
    )

    # ---------- 阿里云 DashScope ----------
    dashscope_api_key: str = ""
    dashscope_base_url: str = "https://dashscope.aliyuncs.com"
    embedding_model: str = "text-embedding-v2"
    embedding_dim: int = 1536          # text-embedding-v2 实际维度

    # ---------- DeepSeek（OpenAI 兼容协议）----------
    deepseek_api_key: str = ""
    deepseek_base_url: str = "https://api.deepseek.com"
    chat_model: str = "deepseek-chat"
    chat_temperature: float = 0.7

    # ---------- PostgreSQL (pgvector) ----------
    database_url: str = "postgresql://postgres:postgres@localhost:5432/postgres"
    db_pool_min_size: int = 1
    db_pool_max_size: int = 10

    # ---------- RAG ----------
    rag_rerank_enabled: bool = True
    rag_rerank_model: str = "gte-rerank"
    rag_similarity_threshold: float = 0.35
    rag_max_top_k: int = 8
    rag_expand_factor: int = 2

    # ---------- SearXNG ----------
    searxng_url: str = "http://localhost:8888/search"

    # ---------- 服务 / HTTP ----------
    server_port: int = 8000
    http_timeout: float = 30.0

    @property
    def dashscope_base(self) -> str:
        """去掉结尾斜杠，便于拼接接口路径。"""
        return self.dashscope_base_url.rstrip("/")

    @property
    def deepseek_base(self) -> str:
        return self.deepseek_base_url.rstrip("/")


@lru_cache
def get_settings() -> Settings:
    """单例配置，避免重复解析环境变量。"""
    return Settings()
