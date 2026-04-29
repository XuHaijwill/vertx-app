-- ================================================================
-- V3__scheduled_tasks.sql
-- Flyway Migration V3 — Scheduled task table
-- ================================================================

CREATE TABLE IF NOT EXISTS scheduled_tasks (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    description   TEXT,
    task_type     VARCHAR(50)  NOT NULL DEFAULT 'HTTP',
    -- task_type values: HTTP, SQL, SHELL
    config        JSONB        NOT NULL DEFAULT '{}',
    -- HTTP config:  { "url": "https://...", "method": "GET", "headers": {}, "body": "..." }
    -- SQL config:   { "sql": "SELECT ...", "params": [] }
    -- SHELL config: { "command": "..." }
    cron          VARCHAR(100),        -- cron expression, e.g. "0 0 * * * ?" (at top of every hour)
    next_run_time TIMESTAMP,           -- next scheduled run (UTC)
    last_run_time TIMESTAMP,           -- last actual execution time
    last_run_status VARCHAR(50),       -- SUCCESS, FAILED, PARTIAL
    last_run_message TEXT,
    run_count     BIGINT      DEFAULT 0,       -- total execution count
    status        VARCHAR(20) DEFAULT 'ACTIVE', -- ACTIVE, PAUSED, DELETED
    created_at    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE scheduled_tasks IS 'Stores scheduled task definitions. The SchedulerVerticle polls this table and executes due tasks.';

CREATE INDEX IF NOT EXISTS idx_scheduled_tasks_status      ON scheduled_tasks(status);
CREATE INDEX IF NOT EXISTS idx_scheduled_tasks_next_run_time ON scheduled_tasks(next_run_time) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_scheduled_tasks_type          ON scheduled_tasks(task_type);

-- Insert a sample task (data sync — runs every hour)
INSERT INTO scheduled_tasks (name, description, task_type, config, cron, next_run_time, status)
VALUES (
    'hourly-data-sync',
    'Hourly data synchronization check',
    'HTTP',
    '{"url": "http://localhost:8888/support/api/products", "method": "GET", "headers": {}}',
    '0 0 * * * ?',
    CURRENT_TIMESTAMP,
    'PAUSED'
) ON CONFLICT DO NOTHING;