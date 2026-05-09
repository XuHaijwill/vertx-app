package com.example.tasks;

import com.example.db.DatabaseVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AccessLogCleanupTask — 访问日志定时清理任务
 *
 * <p>配合 SchedulerVerticle 使用，定期清理过期的访问日志。
 * 保留天数从 sys_config (key = sys.access-log.retentionDays) 读取，支持运行时动态调整。
 *
 * <p>配置示例：
 * <pre>
 * INSERT INTO scheduled_tasks (name, task_type, config, cron, next_run_time, status)
 * VALUES ('cleanup-access-log', 'CLASS',
 *   '{"class": "com.example.tasks.AccessLogCleanupTask", "method": "cleanup", "async": true, "params": {}}',
 *   '0 0 3 * * ?',  -- 每天凌晨 3:00
 *   CURRENT_TIMESTAMP, 'ACTIVE');
 * </pre>
 *
 * <p>也可以指定固定天数（覆盖 sys_config 配置）：
 * <pre>
 * '{"class": "com.example.tasks.AccessLogCleanupTask", "method": "cleanup", "async": true, "params": {"retentionDays": 30}}'
 * </pre>
 */
public class AccessLogCleanupTask {

    private static final Logger LOG = LoggerFactory.getLogger(AccessLogCleanupTask.class);

    /**
     * 清理过期访问日志（异步方法，供 SchedulerVerticle 调用）
     *
     * @param vertx  Vertx 实例
     * @param params 配置参数：{retentionDays: 90}（可选，为空则读取 sys_config）
     * @return Future&lt;String&gt; 清理结果描述
     */
    public static Future<String> cleanup(Vertx vertx, JsonObject params) {
        Integer overrideDays = params.getInteger("retentionDays", null);

        LOG.info("[AccessLogCleanup] Starting cleanup task");

        if (overrideDays != null && overrideDays > 0) {
            return doCleanup(vertx, overrideDays);
        }

        // Read retention days from sys_config
        return getRetentionDays(vertx)
            .compose(retentionDays -> {
                if (retentionDays <= 0) {
                    String msg = "Auto-cleanup disabled (retentionDays=" + retentionDays + ")";
                    LOG.info("[AccessLogCleanup] {}", msg);
                    return Future.succeededFuture(msg);
                }
                return doCleanup(vertx, retentionDays);
            });
    }

    /**
     * 执行清理
     */
    private static Future<String> doCleanup(Vertx vertx, int retentionDays) {
        String sql = """
            DELETE FROM access_log
            WHERE created_at < CURRENT_TIMESTAMP - ($1 || ' days')::INTERVAL
            """;

        LOG.info("[AccessLogCleanup] Deleting logs older than {} days", retentionDays);

        return DatabaseVerticle.query(vertx, sql, Tuple.of(retentionDays))
            .map(rows -> {
                long count = rows.rowCount();
                String msg = String.format("Deleted %d access log records older than %d days", count, retentionDays);
                LOG.info("[AccessLogCleanup] {}", msg);
                return msg;
            });
    }

    /**
     * 从 sys_config 读取保留天数
     */
    private static Future<Integer> getRetentionDays(Vertx vertx) {
        String sql = "SELECT config_value FROM sys_config WHERE config_key = 'sys.access-log.retentionDays'";
        return DatabaseVerticle.query(vertx, sql)
            .map(rows -> {
                var it = rows.iterator();
                if (it.hasNext()) {
                    try {
                        return Integer.parseInt(it.next().getString("config_value"));
                    } catch (NumberFormatException e) {
                        return 90;
                    }
                }
                return 90;
            });
    }
}
