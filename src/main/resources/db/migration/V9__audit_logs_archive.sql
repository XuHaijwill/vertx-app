-- ================================================================
-- V9__audit_logs_archive.sql — 审计日志归档表
-- ================================================================
-- 归档策略：
--   • audit_logs：保留最近 1 年的活跃数据
--   • audit_logs_archive：保存 1 年以上的历史数据
--   • 大于 6 个月的日志定期迁移到 archive 表
-- ================================================================

-- ================================================================
-- 1. 创建归档表（与主表结构相同）
-- ================================================================

CREATE TABLE IF NOT EXISTS audit_logs_archive (
    LIKE audit_logs INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES
);

-- 添加归档时间戳
ALTER TABLE audit_logs_archive ADD COLUMN archived_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- 归档表按时间分区（按月）
CREATE INDEX idx_audit_archive_time ON audit_logs_archive(created_at DESC);
CREATE INDEX idx_audit_archive_archived ON audit_logs_archive(archived_at DESC);

COMMENT ON TABLE audit_logs_archive IS '审计日志归档表 — 保存 1 年以上的历史审计记录';

-- ================================================================
-- 2. 归档函数：迁移 > 6 个月的日志到 archive 表
-- ================================================================

CREATE OR REPLACE FUNCTION archive_audit_logs(
    p_months_old INTEGER DEFAULT 6,
    p_batch_size INTEGER DEFAULT 10000
) RETURNS INTEGER AS $$
DECLARE
    v_moved INTEGER := 0;
    v_total INTEGER := 0;
    v_cutoff TIMESTAMPTZ;
BEGIN
    v_cutoff := CURRENT_TIMESTAMP - (p_months_old || ' months')::INTERVAL;
    
    -- 批量迁移
    LOOP
        WITH moved AS (
            DELETE FROM audit_logs
            WHERE id IN (
                SELECT id FROM audit_logs
                WHERE created_at < v_cutoff
                LIMIT p_batch_size
            )
            RETURNING *
        )
        INSERT INTO audit_logs_archive (
            id, trace_id, action, entity_type, entity_id,
            old_value, new_value, changed_fields,
            user_id, username, user_ip, user_agent, request_id,
            service_name, duration_ms, status, error_message, extra, created_at
        )
        SELECT id, trace_id, action, entity_type, entity_id,
            old_value, new_value, changed_fields,
            user_id, username, user_ip, user_agent, request_id,
            service_name, duration_ms, status, error_message, extra, created_at
        FROM moved;
        
        GET DIAGNOSTICS v_moved = ROW_COUNT;
        v_total := v_total + v_moved;
        
        EXIT WHEN v_moved = 0;
        
        -- 避免长事务，每批次提交
        COMMIT;
    END LOOP;
    
    RAISE NOTICE 'Archived % audit log records older than % months', v_total, p_months_old;
    RETURN v_total;
END;
$$ LANGUAGE plpgsql;

-- ================================================================
-- 3. 清理函数：删除 archive 表中超过 N 年的记录
-- ================================================================

CREATE OR REPLACE FUNCTION purge_old_audit_logs(
    p_years_old INTEGER DEFAULT 3
) RETURNS INTEGER AS $$
DECLARE
    v_deleted INTEGER;
    v_cutoff TIMESTAMPTZ;
BEGIN
    v_cutoff := CURRENT_TIMESTAMP - (p_years_old || ' years')::INTERVAL;
    
    DELETE FROM audit_logs_archive WHERE created_at < v_cutoff;
    
    GET DIAGNOSTICS v_deleted = ROW_COUNT;
    RAISE NOTICE 'Purged % audit log records older than % years', v_deleted, p_years_old;
    RETURN v_deleted;
END;
$$ LANGUAGE plpgsql;

-- ================================================================
-- 4. 查询视图：统一查询主表 + 归档表
-- ================================================================

CREATE OR REPLACE VIEW audit_logs_all AS
SELECT id, trace_id, action, entity_type, entity_id,
       old_value, new_value, changed_fields,
       user_id, username, user_ip, user_agent, request_id,
       service_name, duration_ms, status, error_message, extra,
       created_at, FALSE AS is_archived
FROM audit_logs
UNION ALL
SELECT id, trace_id, action, entity_type, entity_id,
       old_value, new_value, changed_fields,
       user_id, username, user_ip, user_agent, request_id,
       service_name, duration_ms, status, error_message, extra,
       created_at, TRUE AS is_archived
FROM audit_logs_archive;

COMMENT ON VIEW audit_logs_all IS '审计日志统一视图 — 包含活跃数据和历史归档';

-- ================================================================
-- 5. 定期归档任务（可选，配合 SchedulerVerticle）
-- ================================================================

-- 方式一：在 scheduled_tasks 表中添加归档任务
-- INSERT INTO scheduled_tasks (name, task_type, config, cron, next_run_time, status)
-- VALUES ('archive-audit-logs', 'SQL',
--   '{"sql": "SELECT archive_audit_logs(6, 10000)"}',
--   '0 0 4 * * ?',  -- 每天凌晨 4:00
--   CURRENT_TIMESTAMP, 'ACTIVE');

-- 方式二：创建 pg_cron 扩展（如果可用）
-- SELECT cron.schedule('archive-audit', '0 4 * * *', 
--   $$SELECT archive_audit_logs(6, 10000)$$);

-- ================================================================
-- 6. 触发器：自动归档（可选，不推荐高并发场景）
-- ================================================================

-- 注意：触发器方式会影响写入性能，建议用定时任务代替
-- CREATE OR REPLACE FUNCTION auto_archive_audit_log() RETURNS TRIGGER AS $$
-- BEGIN
--     -- 仅在插入时检查是否需要归档（通常不需要）
--     RETURN NEW;
-- END;
-- $$ LANGUAGE plpgsql;
