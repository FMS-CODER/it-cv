-- PostgreSQL：与 ChatDO / ChatMessageDO（MyBatis-Plus 下划线映射）对齐
-- 在目标库（如 robot）中手动执行一次

CREATE TABLE IF NOT EXISTS t_chat (
    id           BIGSERIAL PRIMARY KEY,
    uuid         VARCHAR(64)  NOT NULL,
    summary      VARCHAR(512) NOT NULL DEFAULT '',
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_t_chat_uuid ON t_chat (uuid);
CREATE INDEX IF NOT EXISTS idx_t_chat_update_time ON t_chat (update_time DESC);

CREATE TABLE IF NOT EXISTS t_chat_message (
    id                 BIGSERIAL PRIMARY KEY,
    chat_uuid          VARCHAR(64) NOT NULL,
    content            TEXT,
    reasoning_content  TEXT,
    role               VARCHAR(32),
    create_time        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_t_chat_message_chat_time ON t_chat_message (chat_uuid, create_time DESC);

COMMENT ON TABLE t_chat IS '对话会话';
COMMENT ON TABLE t_chat_message IS '对话消息';
