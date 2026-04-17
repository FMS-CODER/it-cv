-- =============================================================================
-- PostgreSQL 全量建表脚本（汇总）
-- 对应后端 MyBatis-Plus 实体：ChatDO、ChatMessageDO、FileChunkInfoDO、
-- AiCustomerServiceFileStorageDO；另含可选知识库表（需 pgvector 扩展）
-- 在目标库中按需整段执行；空库可一次执行本文件
-- =============================================================================
SET search_path TO cv_schema, public;

-- -----------------------------------------------------------------------------
-- 1. 对话会话 t_chat（ChatDO）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_chat (
    id           BIGSERIAL PRIMARY KEY,
    uuid         VARCHAR(64)  NOT NULL,
    summary      VARCHAR(512) NOT NULL DEFAULT '',
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_t_chat_uuid ON t_chat (uuid);
CREATE INDEX IF NOT EXISTS idx_t_chat_update_time ON t_chat (update_time DESC);

COMMENT ON TABLE t_chat IS '对话会话';

-- -----------------------------------------------------------------------------
-- 2. 对话消息 t_chat_message（ChatMessageDO）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_chat_message (
    id                 BIGSERIAL PRIMARY KEY,
    chat_uuid          VARCHAR(64) NOT NULL,
    content            TEXT,
    reasoning_content  TEXT,
    role               VARCHAR(32),
    create_time        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_t_chat_message_chat_time ON t_chat_message (chat_uuid, create_time DESC);

COMMENT ON TABLE t_chat_message IS '对话消息';
COMMENT ON COLUMN t_chat_message.reasoning_content IS '模型推理过程（如 DeepSeek reasoner）';

-- -----------------------------------------------------------------------------
-- 3. 分片信息 t_file_chunk_info（FileChunkInfoDO）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_file_chunk_info (
    id           BIGSERIAL PRIMARY KEY,
    file_md5     VARCHAR(64),
    chunk_number INTEGER,
    chunk_path   VARCHAR(1024),
    chunk_size   BIGINT,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_t_file_chunk_info_md5 ON t_file_chunk_info (file_md5);

COMMENT ON TABLE t_file_chunk_info IS '大文件分片上传信息';

-- -----------------------------------------------------------------------------
-- 4. AI 客服文件存储 t_ai_customer_service_file_storage（AiCustomerServiceFileStorageDO）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_ai_customer_service_file_storage (
    id               BIGSERIAL PRIMARY KEY,
    file_md5         VARCHAR(64),
    file_name        VARCHAR(512),
    file_path        VARCHAR(1024),
    file_size        BIGINT,
    total_chunks     INTEGER,
    uploaded_chunks  INTEGER,
    status           INTEGER,
    remark           VARCHAR(1024),
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_cs_file_md5 ON t_ai_customer_service_file_storage (file_md5);

COMMENT ON TABLE t_ai_customer_service_file_storage IS 'AI 客服 Markdown 等大文件存储与分片进度';

-----------------------------------------------------------------------------
5.（可选）简历知识库 resume_knowledge_base — 需先安装 pgvector
   若未使用向量检索，可跳过本节
-----------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS resume_knowledge_base (
    id BIGSERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    metadata JSONB,
    embedding vector(1536),
    category VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_resume_kb_embedding ON resume_knowledge_base USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_resume_kb_category ON resume_knowledge_base(category);
CREATE INDEX IF NOT EXISTS idx_resume_kb_created_at ON resume_knowledge_base(created_at DESC);

COMMENT ON TABLE resume_knowledge_base IS '简历优化知识库（向量）';

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS update_resume_kb_updated_at ON resume_knowledge_base;
CREATE TRIGGER update_resume_kb_updated_at
    BEFORE UPDATE ON resume_knowledge_base
    FOR EACH ROW
    EXECUTE PROCEDURE update_updated_at_column();
