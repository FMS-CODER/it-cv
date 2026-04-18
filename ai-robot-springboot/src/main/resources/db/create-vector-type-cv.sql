-- =============================================================================
-- 在cv架构中创建vector类型（如果pgvector扩展安装在public架构中）
-- 执行此脚本前请确保已安装pgvector扩展
-- =============================================================================

-- 设置搜索路径到cv架构
SET search_path TO cv;

-- 方法1：在cv架构中创建vector类型（作为public.vector的别名）
-- 如果public架构中已有vector类型，可以在cv架构中创建类型别名
DO $$
BEGIN
    -- 检查cv架构中是否已存在vector类型
    IF NOT EXISTS (
        SELECT 1 FROM pg_type t
        JOIN pg_namespace n ON n.oid = t.typnamespace
        WHERE n.nspname = 'cv' AND t.typname = 'vector'
    ) THEN
        -- 在cv架构中创建vector类型，基于public.vector
        EXECUTE 'CREATE TYPE cv.vector AS (public.vector)';
        RAISE NOTICE '已在cv架构中创建vector类型';
    ELSE
        RAISE NOTICE 'cv架构中已存在vector类型';
    END IF;
END
$$;

-- 方法2：如果上述方法不行，可以创建域（domain）
-- CREATE DOMAIN cv.vector AS public.vector;

-- 验证类型是否创建成功
SELECT 
    n.nspname as schema_name,
    t.typname as type_name,
    pg_catalog.format_type(t.typbasetype, t.typtypmod) as base_type
FROM pg_type t
JOIN pg_namespace n ON n.oid = t.typnamespace
WHERE t.typname = 'vector' AND n.nspname IN ('cv', 'public')
ORDER BY n.nspname;