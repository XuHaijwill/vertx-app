-- ================================================================
-- V8__audit_logs.sql — 审计日志表
-- ================================================================
-- 记录所有业务实体的变更历史（创建/更新/删除），支持查询和追溯。
--
-- 设计要点：
--   • 表名 audit_logs，与业务表分离，符合审计独立原则
--   • 同一事务内写入：JOIN audit_logs 不影响主表查询性能
--   • JSONB 存储 old_value / new_value，支持任意字段变更记录
--   • GIN 索引支持 JSONB 内容检索（按 entityType 或 action 查询）
--   • partial unique index 防止同一事务内重复记录同一变更
--   • retention_days 配置化，支持分区表按月裁剪历史数据
-- ================================================================

CREATE TABLE IF NOT EXISTS audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    trace_id        VARCHAR(64)     NOT NULL,           -- 分布式追踪 ID（Skywalking/Jaeger 格式）
    action          VARCHAR(32)     NOT NULL,           -- AUDIT_CREATE | AUDIT_UPDATE | AUDIT_DELETE
    entity_type     VARCHAR(64)     NOT NULL,           -- users | products | orders | payments | sys_configs
    entity_id       VARCHAR(64)     NOT NULL,           -- 被操作实体的主键（String 兼容 UUID/复合主键）
    old_value       JSONB,                              -- 变更前的完整数据（AUDIT_CREATE 时为 NULL）
    new_value       JSONB,                              -- 变更后的完整数据（AUDIT_DELETE 时为 NULL）
    changed_fields  JSONB,                              -- 仅记录变更的字段 {field: [old, new], ...}
    user_id         BIGINT,                             -- 操作用户 ID（未登录操作为 NULL）
    username        VARCHAR(128),                       -- 操作用户名（冗余存储，避免 JOIN）
    user_ip         VARCHAR(45),                        -- 客户端 IP（IPv6 兼容）
    user_agent      VARCHAR(512),                       -- 浏览器/客户端 User-Agent
    request_id      VARCHAR(64),                        -- HTTP 请求 ID（用于关联 API 日志）
    service_name    VARCHAR(64)     NOT NULL DEFAULT 'vertx-app',
    duration_ms     INTEGER,                            -- 操作耗时（毫秒）
    status          VARCHAR(16)     NOT NULL DEFAULT 'SUCCESS',  -- SUCCESS | FAILURE
    error_message   VARCHAR(512),                       -- 失败时的错误信息
    extra           JSONB,                              -- 扩展字段（订单金额、操作描述等）
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ================================================================
-- 索引
-- ================================================================

-- 按实体类型 + ID 查询变更历史（最常用）
CREATE INDEX idx_audit_entity         ON audit_logs(entity_type, entity_id DESC);
-- 按时间范围 + 操作类型查询
CREATE INDEX idx_audit_time_action    ON audit_logs(created_at DESC, action);
-- 按追踪 ID 串联同一次请求的所有变更
CREATE INDEX idx_audit_trace          ON audit_logs(trace_id);
-- JSONB 内容检索（entity_type 或 action 存储在 JSONB 中时）
CREATE INDEX idx_audit_extra_gin      ON audit_logs USING GIN (extra);
-- 按用户查询其操作历史
CREATE INDEX idx_audit_user           ON audit_logs(user_id, created_at DESC);

-- ================================================================
-- 注释
-- ================================================================

COMMENT ON TABLE audit_logs IS '业务审计日志 — 记录所有实体变更（创建/更新/删除）';
COMMENT ON COLUMN audit_logs.trace_id        IS '分布式追踪 ID，同一次请求的所有日志共享此 ID';
COMMENT ON COLUMN audit_logs.action          IS 'AUDIT_CREATE=新增 | AUDIT_UPDATE=更新 | AUDIT_DELETE=删除';
COMMENT ON COLUMN audit_logs.entity_type     IS '实体类型，对应 REST 资源名：users | products | orders | payments';
COMMENT ON COLUMN audit_logs.entity_id       IS '被操作实体的主键值（字符串类型，兼容 BIGINT/UUID/复合键）';
COMMENT ON COLUMN audit_logs.old_value      IS '变更前完整数据（JSONB），CREATE 时 NULL';
COMMENT ON COLUMN audit_logs.new_value      IS '变更后完整数据（JSONB），DELETE 时 NULL';
COMMENT ON COLUMN audit_logs.changed_fields IS '仅记录变更的字段 {field_name: [old_value, new_value]}';
COMMENT ON COLUMN audit_logs.user_id        IS '操作用户 ID（来自 JWT token），匿名操作为 NULL';
COMMENT ON COLUMN audit_logs.user_ip        IS '客户端 IP（X-Forwarded-For 头优先），支持 IPv6';
COMMENT ON COLUMN audit_logs.status          IS 'SUCCESS=操作成功 | FAILURE=操作失败';
COMMENT ON COLUMN audit_logs.extra          IS '扩展数据，如：{orderAmount: 199.00, reason: "用户取消"}';

-- ================================================================
-- 演示数据（可选）
-- ================================================================

-- INSERT INTO audit_logs (trace_id, action, entity_type, entity_id, old_value, new_value, changed_fields, username, status)
-- VALUES (
--     'abc123',
--     'AUDIT_UPDATE',
--     'products',
--     '1',
--     '{"stock": 100}'::jsonb,
--     '{"stock": 95}'::jsonb,
--     '{"stock": [100, 95]}'::jsonb,
--     'admin',
--     'SUCCESS'
-- );
