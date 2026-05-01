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


--新增功能
--CLASS 任务类型：通过反射调用 Java 类的 public static 方法
--
--两种调用模式：
--
--同步：public static String method(JsonObject params) — 返回 String
--异步：public static Future<String> method(Vertx vertx, JsonObject params) — 返回 Future
-- 1. SQL 任务（清理旧日志）
INSERT INTO scheduled_tasks (name, task_type, config, cron, next_run_time, status)
VALUES ('cleanup-logs', 'SQL',
  '{"sql": "DELETE FROM logs WHERE created_at < NOW() - INTERVAL ''30 days''"}',
  '0 0 3 * * ?',  -- 每天 3:00
  CURRENT_TIMESTAMP, 'ACTIVE');

--2. HTTP 任务（健康检查）
INSERT INTO scheduled_tasks (name, task_type, config, cron, next_run_time, status)
VALUES ('health-check', 'HTTP',
  '{"url": "http://localhost:8888/api/health", "method": "GET"}',
  '0 */5 * * * ?',  -- 每 5 分钟
  CURRENT_TIMESTAMP, 'ACTIVE');


--3. CLASS 任务（同步方法）
INSERT INTO scheduled_tasks (name, task_type, config, cron, next_run_time, status)
VALUES ('daily-report', 'CLASS',
  '{"class": "com.example.tasks.ExampleTasks", "method": "generateDailyReport", "params": {"type": "sales"}}',
  '0 0 8 * * ?',  -- 每天 8:00
  CURRENT_TIMESTAMP, 'ACTIVE');


--4. CLASS 任务（异步方法）
INSERT INTO scheduled_tasks (name, task_type, config, cron, next_run_time, status)
VALUES ('cleanup-old', 'CLASS',
  '{"class": "com.example.tasks.ExampleTasks", "method": "cleanupOldRecords", "async": true, "params": {"table": "logs", "daysToKeep": 30}}',
  '0 0 3 * * ?',  -- 每天 3:00
  CURRENT_TIMESTAMP, 'ACTIVE');
