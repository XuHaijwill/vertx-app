-- ================================================================
-- V13__access_log.sql — 用户访问日志表
-- ================================================================
-- 记录所有用户对 /api/* 端点的访问行为，支持：
--   • 自定义保留天数（通过 sys_config 配置）
--   • 定时清理过期记录（配合 SchedulerVerticle）
--   • 多维度查询：用户、路径、方法、状态码、IP、时间范围
-- ================================================================

CREATE TABLE IF NOT EXISTS access_log (
    id              BIGSERIAL PRIMARY KEY,
    trace_id        VARCHAR(64),                        -- 请求追踪 ID
    user_id         BIGINT,                             -- 操作用户 ID（未登录为 NULL）
    username        VARCHAR(128),                       -- 用户名（冗余存储，避免 JOIN）
    method          VARCHAR(10)     NOT NULL,           -- HTTP 方法: GET/POST/PUT/DELETE
    path            VARCHAR(500)    NOT NULL,           -- 请求路径
    query_string    VARCHAR(1000),                      -- 查询参数
    status_code     INTEGER        NOT NULL DEFAULT 0,  -- HTTP 响应状态码
    response_time   INTEGER,                            -- 响应时间（毫秒）
    user_ip         VARCHAR(45),                        -- 客户端 IP
    user_agent      VARCHAR(512),                       -- 浏览器 User-Agent
    request_id      VARCHAR(64),                        -- HTTP 请求 ID
    error_message   VARCHAR(512),                       -- 错误信息（5xx 时记录）
    extra           JSONB,                              -- 扩展字段
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ================================================================
-- 索引
-- ================================================================

-- 按时间范围查询（最常用：列表+清理）
CREATE INDEX idx_access_log_time        ON access_log(created_at DESC);
-- 按用户查询访问历史
CREATE INDEX idx_access_log_user        ON access_log(user_id, created_at DESC);
-- 按路径查询（热点接口分析）
CREATE INDEX idx_access_log_path        ON access_log(path, created_at DESC);
-- 按状态码查询（错误分析）
CREATE INDEX idx_access_log_status      ON access_log(status_code, created_at DESC);
-- 按追踪 ID 查询
CREATE INDEX idx_access_log_trace       ON access_log(trace_id);
-- 按方法+路径组合查询
CREATE INDEX idx_access_log_method_path ON access_log(method, path);

-- ================================================================
-- 注释
-- ================================================================

COMMENT ON TABLE access_log IS '用户访问日志 — 记录所有 API 请求';
COMMENT ON COLUMN access_log.trace_id       IS '分布式追踪 ID，同一次请求的所有日志共享';
COMMENT ON COLUMN access_log.method         IS 'HTTP 方法: GET | POST | PUT | DELETE | PATCH';
COMMENT ON COLUMN access_log.path           IS '请求路径（不含 query string）';
COMMENT ON COLUMN access_log.query_string   IS 'URL 查询参数（? 后的部分）';
COMMENT ON COLUMN access_log.status_code    IS 'HTTP 响应状态码';
COMMENT ON COLUMN access_log.response_time  IS '响应时间（毫秒）';
COMMENT ON COLUMN access_log.user_ip        IS '客户端 IP（X-Forwarded-For / X-Real-IP 优先）';
COMMENT ON COLUMN access_log.extra          IS '扩展数据，如 {bodySize: 1024, contentType: "application/json"}';

-- ================================================================
-- sys_config 种子数据：访问日志保留天数
-- ================================================================

INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
VALUES (
    'Access log retention days',
    'sys.access-log.retentionDays',
    '90',
    'Y',
    'admin',
    NOW(),
    'Access log retention period in days. Logs older than this will be auto-deleted. Default: 90 days. Set to 0 to disable auto-cleanup.'
);
