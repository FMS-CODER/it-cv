-- 创建数据库
-- CREATE DATABASE xiaoha_ai_robot;
-- \c xiaoha_ai_robot;

-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 创建简历优化知识库表
CREATE TABLE IF NOT EXISTS resume_knowledge_base (
    id BIGSERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    metadata JSONB,
    embedding public.vector(1536),
    category VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_resume_kb_embedding ON resume_knowledge_base USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_resume_kb_category ON resume_knowledge_base(category);
CREATE INDEX IF NOT EXISTS idx_resume_kb_created_at ON resume_knowledge_base(created_at DESC);

-- 添加注释
COMMENT ON TABLE resume_knowledge_base IS '简历优化知识库';
COMMENT ON COLUMN resume_knowledge_base.id IS '主键ID';
COMMENT ON COLUMN resume_knowledge_base.content IS '文本内容';
COMMENT ON COLUMN resume_knowledge_base.metadata IS '元数据';
COMMENT ON COLUMN resume_knowledge_base.embedding IS '向量嵌入';
COMMENT ON COLUMN resume_knowledge_base.category IS '分类';
COMMENT ON COLUMN resume_knowledge_base.created_at IS '创建时间';
COMMENT ON COLUMN resume_knowledge_base.updated_at IS '更新时间';

-- 创建更新时间触发器函数
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 创建触发器
DROP TRIGGER IF EXISTS update_resume_kb_updated_at ON resume_knowledge_base;
CREATE TRIGGER update_resume_kb_updated_at
    BEFORE UPDATE ON resume_knowledge_base
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();