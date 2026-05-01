package com.example.tasks;

import com.example.db.DatabaseVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example scheduled tasks for CLASS-type scheduler invocations.
 *
 * <p>All methods must be public static.</p>
 *
 * <h2>Sync vs Async Methods:</h2>
 *
 * <h3>Sync (return String directly):</h3>
 * <pre>
 * public static String myTask(JsonObject params) {
 *     // do work
 *     return "OK: result";
 * }
 * </pre>
 *
 * <h3>Async (return Future&lt;String&gt;):</h3>
 * <pre>
 * public static Future&lt;String&gt; myAsyncTask(Vertx vertx, JsonObject params) {
 *     return vertx.executeBlocking(() -> {
 *         // blocking work
 *         return "OK: async result";
 *     });
 * }
 * </pre>
 *
 * <h2>Usage in scheduled_tasks table:</h2>
 *
 * <pre>
 * -- Sync method
 * INSERT INTO scheduled_tasks (name, task_type, config, cron, next_run_time, status)
 * VALUES ('hello-task', 'CLASS',
 *   '{"class": "com.example.tasks.ExampleTasks", "method": "helloWorld", "params": {"name": "User"}}',
 *   '0 * * * * ?',  -- every minute
 *   CURRENT_TIMESTAMP,
 *   'ACTIVE');
 *
 * -- Async method (with "async": true)
 * INSERT INTO scheduled_tasks (name, task_type, config, cron, next_run_time, status)
 * VALUES ('cleanup-task', 'CLASS',
 *   '{"class": "com.example.tasks.ExampleTasks", "method": "cleanupOldRecords", "async": true}',
 *   '0 0 3 * * ?',  -- daily at 3:00 AM
 *   CURRENT_TIMESTAMP,
 *   'ACTIVE');
 * </pre>
 */
public class ExampleTasks {

    private static final Logger LOG = LoggerFactory.getLogger(ExampleTasks.class);

    // ================================================================
    // Sync methods (return String directly)
    // ================================================================

    /**
     * Simple hello world sync task.
     *
     * @param params task parameters from config
     * @return result message
     */
    public static String helloWorld(JsonObject params) {
        String name = params.getString("name", "World");
        String result = "Hello, " + name + "! Time: " + java.time.Instant.now();
        LOG.info("[TASK] helloWorld executed: {}", result);
        return result;
    }

    /**
     * Generate daily report (sync version).
     *
     * @param params task parameters (e.g., {"type": "sales"})
     * @return report summary
     */
    public static String generateDailyReport(JsonObject params) {
        String type = params.getString("type", "general");
        // In real code, query DB, generate report file, send email, etc.
        String report = String.format("[%s] Daily %s report generated at %s",
            type.toUpperCase(),
            type,
            java.time.LocalDate.now());
        LOG.info("[TASK] {}", report);
        return report;
    }

    // ================================================================
    // Async methods (return Future&lt;String&gt;, require "async": true in config)
    // ================================================================

    /**
     * Cleanup old records (async version with DB access).
     *
     * <p>This method demonstrates async execution with database operations.</p>
     *
     * @param vertx  Vert.x instance (injected by scheduler)
     * @param params task parameters
     * @return Future with cleanup summary
     */
    public static Future<String> cleanupOldRecords(Vertx vertx, JsonObject params) {
        int daysToKeep = params.getInteger("daysToKeep", 30);
        String tableName = params.getString("table", "logs");

        LOG.info("[TASK] Starting cleanup: table={}, daysToKeep={}", tableName, daysToKeep);

        // Example: delete records older than N days
        String sql = String.format(
            "DELETE FROM %s WHERE created_at < NOW() - INTERVAL '%d days'",
            tableName, daysToKeep);

        return DatabaseVerticle.query(vertx, sql)
            .map(rows -> {
                int deleted = rows.rowCount();
                String msg = String.format("Cleaned up %d old records from %s", deleted, tableName);
                LOG.info("[TASK] {}", msg);
                return msg;
            })
            .onFailure(err -> LOG.error("[TASK] Cleanup failed", err));
    }

    /**
     * Sync inventory from external API (async with HTTP call).
     *
     * @param vertx  Vert.x instance
     * @param params task parameters (e.g., {"apiUrl": "https://..."})
     * @return Future with sync summary
     */
    public static Future<String> syncInventory(Vertx vertx, JsonObject params) {
        String apiUrl = params.getString("apiUrl", "https://api.example.com/inventory");

        LOG.info("[TASK] Syncing inventory from: {}", apiUrl);

        // In real code: HTTP request to external API, update local DB
        return vertx.executeBlocking(() -> {
            // Simulate external API call
            try {
                Thread.sleep(1000); // simulate network latency
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String msg = String.format("Synced inventory from %s at %s", apiUrl, java.time.Instant.now());
            LOG.info("[TASK] {}", msg);
            return msg;
        });
    }

    /**
     * Send batch notifications (async with multiple operations).
     *
     * @param vertx  Vert.x instance
     * @param params task parameters
     * @return Future with notification summary
     */
    public static Future<String> sendBatchNotifications(Vertx vertx, JsonObject params) {
        String channel = params.getString("channel", "email");
        int batchSize = params.getInteger("batchSize", 100);

        LOG.info("[TASK] Sending {} notifications via {}", batchSize, channel);

        // Query pending notifications and send
        String sql = "SELECT id, user_id, message FROM notifications WHERE status = 'PENDING' LIMIT $1";

        return DatabaseVerticle.query(vertx, sql)
            .compose(rows -> {
                int count = rows.rowCount();
                if (count == 0) {
                    return Future.succeededFuture("No pending notifications");
                }

                // In real code: iterate rows and send notifications
                LOG.info("[TASK] Processing {} notifications", count);
                return Future.succeededFuture(String.format("Sent %d notifications via %s", count, channel));
            })
            .onFailure(err -> LOG.error("[TASK] Notification batch failed", err));
    }
}
