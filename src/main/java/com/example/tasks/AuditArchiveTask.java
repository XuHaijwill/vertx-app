package com.example.tasks;

import com.example.db.DatabaseVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AuditArchiveTask — 审计日志归档定时任务
 *
 * <p>配合 SchedulerVerticle 使用，定期执行：
 * <ul>
 *   <li>迁移 > 6 个月的日志到 audit_logs_archive</li>
 *   <li>清理 > 3 年的历史归档记录</li>
 * </ul>
 *
 * <p>配置示例：
 * <pre>
 * INSERT INTO scheduled_tasks (name, task_type, config, cron, next_run_time, status)
 * VALUES ('archive-audit-logs', 'CLASS',
 *   '{"class": "com.example.tasks.AuditArchiveTask", "method": "archive", "async": true, "params": {"monthsOld": 6}}',
 *   '0 0 4 * * ?',  -- 每天凌晨 4:00
 *   CURRENT_TIMESTAMP, 'ACTIVE');
 *
 * VALUES ('purge-old-audit', 'CLASS',
 *   '{"class": "com.example.tasks.AuditArchiveTask", "method": "purge", "async": true, "params": {"yearsOld": 3}}',
 *   '0 0 5 1 * ?',  -- 每月 1 日凌晨 5:00
 *   CURRENT_TIMESTAMP, 'ACTIVE');
 * </pre>
 */
public class AuditArchiveTask {

    private static final Logger LOG = LoggerFactory.getLogger(AuditArchiveTask.class);

    // ================================================================
    // 归档任务：迁移 > N 个月的日志到 archive 表
    // ================================================================

    /**
     * 归档审计日志（异步方法，供 SchedulerVerticle 调用）
     *
     * @param vertx  Vertx 实例
     * @param params 配置参数：{monthsOld: 6, batchSize: 10000}
     * @return Future&lt;String&gt; 归档结果描述
     */
    public static Future<String> archive(Vertx vertx, JsonObject params) {
        int monthsOld = params.getInteger("monthsOld", 6);
        int batchSize = params.getInteger("batchSize", 10000);

        LOG.info("[AuditArchive] Starting archive task: monthsOld={}, batchSize={}", monthsOld, batchSize);

        String sql = "SELECT archive_audit_logs($1, $2) AS moved_count";
        Tuple tuple = Tuple.of(monthsOld, batchSize);

        return DatabaseVerticle.query(vertx, sql, tuple)
            .map(rows -> {
                long count = rows.iterator().next().getLong("moved_count");
                String msg = String.format("Archived %d audit log records older than %d months", count, monthsOld);
                LOG.info("[AuditArchive] {}", msg);
                return msg;
            });
    }

    // ================================================================
    // 清理任务：删除 > N 年的归档记录
    // ================================================================

    /**
     * 清理历史归档（异步方法，供 SchedulerVerticle 调用）
     *
     * @param vertx  Vertx 实例
     * @param params 配置参数：{yearsOld: 3}
     * @return Future&lt;String&gt; 清理结果描述
     */
    public static Future<String> purge(Vertx vertx, JsonObject params) {
        int yearsOld = params.getInteger("yearsOld", 3);

        LOG.info("[AuditArchive] Starting purge task: yearsOld={}", yearsOld);

        String sql = "SELECT purge_old_audit_logs($1) AS deleted_count";
        Tuple tuple = Tuple.of(yearsOld);

        return DatabaseVerticle.query(vertx, sql, tuple)
            .map(rows -> {
                long count = rows.iterator().next().getLong("deleted_count");
                String msg = String.format("Purged %d audit log records older than %d years", count, yearsOld);
                LOG.info("[AuditArchive] {}", msg);
                return msg;
            });
    }

    // ================================================================
    // 查询归档统计
    // ================================================================

    /**
     * 获取归档统计信息（异步方法）
     *
     * @param vertx  Vertx 实例
     * @param params 无参数
     * @return Future&lt;String&gt; 统计信息 JSON
     */
    public static Future<String> stats(Vertx vertx, JsonObject params) {
        String sql = """
            SELECT
                (SELECT COUNT(*) FROM audit_logs) AS active_count,
                (SELECT COUNT(*) FROM audit_logs_archive) AS archived_count,
                (SELECT MIN(created_at) FROM audit_logs) AS oldest_active,
                (SELECT MIN(created_at) FROM audit_logs_archive) AS oldest_archived
            """;

        return DatabaseVerticle.query(vertx, sql, Tuple.tuple())
            .map(rows -> {
                var row = rows.iterator().next();
                return new JsonObject()
                    .put("activeCount", row.getLong("active_count"))
                    .put("archivedCount", row.getLong("archived_count"))
                    .put("oldestActive", row.getValue("oldest_active"))
                    .put("oldestArchived", row.getValue("oldest_archived"))
                    .encode();
            });
    }
}
