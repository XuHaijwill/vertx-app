package com.example.verticles;

import com.example.core.Config;
import com.example.db.DatabaseVerticle;
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.parsetools.JsonParser;
import io.vertx.core.streams.ReadStream;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SchedulerVerticle — polls the scheduled_tasks table and fires tasks when they are due.
 *
 * <p>Design:</p>
 * <ul>
 *   <li>Polls DB every N seconds (configurable via scheduler.poll-seconds)</li>
 *   <li>Executes tasks whose next_run_time &lt;= now AND status = 'ACTIVE'</li>
 *   <li>Supports task types: HTTP, SQL</li>
 *   <li>Updates last_run_time, last_run_status, last_run_message, run_count,
 *       next_run_time (computed from cron expression) after each execution</li>
 *   <li>Uses a ConcurrentHashMap to track currently-running tasks (prevent concurrent runs
 *       of the same task)</li>
 * </ul>
 *
 * <p>To add a new scheduled task, insert a row into the scheduled_tasks table:</p>
 * <pre>
 * INSERT INTO scheduled_tasks (name, task_type, config, cron, next_run_time, status)
 * VALUES ('my-task', 'HTTP',
 *   '{"url": "http://localhost:8888/support/api/...", "method": "GET"}',
 *   '0 0 * * * ?',   -- cron: at the top of every hour
 *   CURRENT_TIMESTAMP,
 *   'ACTIVE');
 * </pre>
 */
public class SchedulerVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(SchedulerVerticle.class);

    /** Currently-executing task IDs to prevent concurrent runs of the same task. */
    private final ConcurrentHashMap<Long, AtomicBoolean> runningTasks = new ConcurrentHashMap<>();

    private long pollTimerId = -1;
    private WebClient webClient;

    // cron-parser is thread-safe and stateless — one instance is enough
    private static final CronParser PARSER = new CronParser(
        CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ));

    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"));

    // ================================================================
    // Lifecycle
    // ================================================================

    @Override
    public void start(Promise<Void> startPromise) {
        if (!Config.isSchedulerEnabled(config())) {
            LOG.info("[SCHEDULER] Disabled via config — not starting");
            startPromise.complete();
            return;
        }

        int pollSecs = Config.getSchedulerPollSeconds(config());
        int httpTimeoutSecs = Config.getSchedulerHttpTimeoutSeconds(config());

        LOG.info("[SCHEDULER] Starting — poll interval={}s, httpTimeout={}s",
            pollSecs, httpTimeoutSecs);

        WebClientOptions opts = new WebClientOptions()
            .setConnectTimeout(httpTimeoutSecs * 1000)
            .setIdleTimeout(httpTimeoutSecs)
            .setFollowRedirects(true)
            .setUserAgent("vertx-app-scheduler/1.0");
        this.webClient = WebClient.create(vertx, opts);

        // Immediate first poll, then repeat
        pollAndExecute();
        pollTimerId = vertx.setTimer(pollSecs * 1000L, id -> scheduleNext(pollSecs));

        LOG.info("[SCHEDULER] Verticle started");
        startPromise.complete();
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        if (pollTimerId > 0) {
            vertx.cancelTimer(pollTimerId);
            LOG.info("[SCHEDULER] Poll timer cancelled");
        }
        if (webClient != null) {
            webClient.close();
        }
        LOG.info("[SCHEDULER] Verticle stopped — {} tasks were mid-run",
            runningTasks.size());
        stopPromise.complete();
    }

    // ================================================================
    // Scheduling loop
    // ================================================================

    private void scheduleNext(int pollSecs) {
        pollAndExecute();
        pollTimerId = vertx.setTimer(pollSecs * 1000L, id -> scheduleNext(pollSecs));
    }

    private void pollAndExecute() {
        String sql =
            "SELECT id, name, task_type, config, cron, next_run_time " +
            "FROM   scheduled_tasks " +
            "WHERE  status = 'ACTIVE' " +
            "  AND  next_run_time IS NOT NULL " +
            "  AND  next_run_time <= NOW() " +
            "ORDER BY next_run_time ASC";

        DatabaseVerticle.query(vertx, sql)
            .onSuccess(rows -> {
                List<JsonObject> tasks = DatabaseVerticle.toJsonList(rows);
                if (!tasks.isEmpty()) {
                    LOG.info("[SCHEDULER] {} task(s) due", tasks.size());
                    for (JsonObject task : tasks) {
                        executeTask(task);
                    }
                }
            })
            .onFailure(err ->
                LOG.error("[SCHEDULER] Failed to poll scheduled_tasks", err));
    }

    // ================================================================
    // Task execution
    // ================================================================

    private void executeTask(JsonObject task) {
        long taskId = task.getLong("id");
        String name = task.getString("name");
        String taskType = task.getString("task_type");
        JsonObject cfg = task.getJsonObject("config");

        // Guard: skip if already running
        AtomicBoolean guard = runningTasks.computeIfAbsent(taskId, id -> new AtomicBoolean(false));
        if (!guard.compareAndSet(false, true)) {
            LOG.warn("[SCHEDULER] Task {} '{}' is still running — skipping this cycle", taskId, name);
            return;
        }

        LOG.info("[SCHEDULER] Executing task id={} name='{}' type={}", taskId, name, taskType);

        long startMs = System.currentTimeMillis();
        ZonedDateTime runTime = ZonedDateTime.now(ZoneId.of("UTC"));

        executeByType(taskType, cfg)
            .onSuccess(result -> {
                long elapsedMs = System.currentTimeMillis() - startMs;
                ZonedDateTime nextRun = computeNextRun(task.getString("cron"), runTime);
                updateTaskRecord(taskId, "SUCCESS", result, elapsedMs, nextRun);
                LOG.info("[SCHEDULER] Task {} '{}' SUCCESS in {}ms — next run {}",
                    taskId, name, elapsedMs, nextRun != null ? TS_FMT.format(nextRun) : "N/A");
            })
            .onFailure(err -> {
                long elapsedMs = System.currentTimeMillis() - startMs;
                ZonedDateTime nextRun = computeNextRun(task.getString("cron"), runTime);
                updateTaskRecord(taskId, "FAILED", err.getMessage(), elapsedMs, nextRun);
                LOG.error("[SCHEDULER] Task {} '{}' FAILED after {}ms: {}",
                    taskId, name, elapsedMs, err.getMessage());
            })
            .onComplete(ar -> guard.set(false));
    }

    // ================================================================
    // Task-type dispatch
    // ================================================================

    private Future<String> executeByType(String taskType, JsonObject cfg) {
        return switch (taskType.toUpperCase()) {
            case "HTTP" -> executeHttpTask(cfg);
            case "SQL"  -> executeSqlTask(cfg);
            default     -> Future.failedFuture("Unknown task type: " + taskType);
        };
    }

    // ---------------------------------------------------------------
    // HTTP task
    // ---------------------------------------------------------------

    private Future<String> executeHttpTask(JsonObject cfg) {
        String url     = cfg.getString("url");
        String method  = cfg.getString("method", "GET").toUpperCase();
        JsonObject headers = cfg.getJsonObject("headers", new JsonObject());
        String body    = cfg.getString("body");

        if (url == null || url.isBlank()) {
            return Future.failedFuture("HTTP task: 'url' is required");
        }

        Promise<String> p = Promise.promise();

        var req = switch (method) {
            case "POST"  -> webClient.postAbs(url);
            case "PUT"   -> webClient.putAbs(url);
            case "DELETE" -> webClient.deleteAbs(url);
            case "PATCH" -> webClient.patchAbs(url);
            default      -> webClient.getAbs(url);
        };

        // Set headers
        headers.fieldNames().forEach(key ->
            req.putHeader(key, headers.getString(key)));

        // Send (Vert.x 5: send/sendBuffer return Future, attach handler via onComplete)
        if (body != null && !body.isBlank()) {
            req.putHeader("Content-Type", "application/json");
            req.sendBuffer(io.vertx.core.buffer.Buffer.buffer(body))
               .onComplete(handleResponse(p, method, url));
        } else {
            req.send()
               .onComplete(handleResponse(p, method, url));
        }

        return p.future();
    }

    private Handler<AsyncResult<HttpResponse<io.vertx.core.buffer.Buffer>>> handleResponse(
            Promise<String> p, String method, String url) {
        return ar -> {
            if (ar.failed()) {
                p.fail("HTTP " + method + " " + url + " — connection error: " + ar.cause().getMessage());
                return;
            }
            HttpResponse<io.vertx.core.buffer.Buffer> resp = ar.result();
            int status = resp.statusCode();
            String body = resp.bodyAsString();

            if (status >= 200 && status < 300) {
                String summary = body != null && body.length() > 200
                    ? body.substring(0, 200) + "..."
                    : (body != null ? body : "(empty)");
                p.complete(String.format("HTTP %d — %s", status, summary));
            } else {
                p.fail(String.format("HTTP %d — %s", status,
                    body != null && body.length() > 200 ? body.substring(0, 200) + "..." : body));
            }
        };
    }

    // ---------------------------------------------------------------
    // SQL task
    // ---------------------------------------------------------------

    private Future<String> executeSqlTask(JsonObject cfg) {
        String sql = cfg.getString("sql");
        if (sql == null || sql.isBlank()) {
            return Future.failedFuture("SQL task: 'sql' is required");
        }

        // Simple passthrough — be very careful in production (consider whitelisting)
        String normalized = sql.trim().toUpperCase();
        if (normalized.startsWith("SELECT") || normalized.startsWith("WITH")) {
            return DatabaseVerticle.query(vertx, sql)
                .map(rows -> {
                    List<JsonObject> results = DatabaseVerticle.toJsonList(rows);
                    return String.format("SELECT returned %d row(s): %s",
                        results.size(),
                        results.isEmpty() ? "[]" : results.get(0).encode());
                });
        } else {
            return DatabaseVerticle.query(vertx, sql)
                .map(rows -> String.format("OK — %d row(s) affected", rows.rowCount()));
        }
    }

    // ================================================================
    // Cron helpers
    // ================================================================

    private ZonedDateTime computeNextRun(String cronExpr, ZonedDateTime fromTime) {
        if (cronExpr == null || cronExpr.isBlank()) return null;
        try {
            Cron cron = PARSER.parse(cronExpr);
            ExecutionTime execTime = ExecutionTime.forCron(cron);
            Optional<ZonedDateTime> next = execTime.nextExecution(fromTime);
            return next.orElse(null);
        } catch (Exception e) {
            LOG.warn("[SCHEDULER] Invalid cron expression '{}': {}", cronExpr, e.getMessage());
            return null;
        }
    }

    // ================================================================
    // DB update after execution
    // ================================================================

    private void updateTaskRecord(long taskId, String status, String message,
                                   long elapsedMs, ZonedDateTime nextRun) {
        String nextRunStr = nextRun != null
            ? TS_FMT.format(nextRun) + " +00"
            : null;

        String sql =
            "UPDATE scheduled_tasks SET " +
            "  last_run_time   = NOW(), " +
            "  last_run_status = $1, " +
            "  last_run_message= $2, " +
            "  next_run_time   = " + (nextRunStr != null ? "($3::timestamptz)" : "NULL") + ", " +
            "  run_count       = run_count + 1, " +
            "  updated_at      = NOW() " +
            "WHERE id = $4";

        Tuple params;
        if (nextRunStr != null) {
            params = Tuple.tuple()
                .addString(status)
                .addString(message != null ? message : "")
                .addString(nextRunStr)
                .addLong(taskId);
        } else {
            // No next_run: use a simpler query
            updateTaskSimple(taskId, status, message);
            return;
        }

        DatabaseVerticle.query(vertx, sql, params)
            .onSuccess(rows ->
                LOG.debug("[SCHEDULER] Updated task {} record — status={}", taskId, status))
            .onFailure(err ->
                LOG.error("[SCHEDULER] Failed to update task {} record: {}", taskId, err.getMessage()));
    }

    private void updateTaskSimple(long taskId, String status, String message) {
        String sql =
            "UPDATE scheduled_tasks SET " +
            "  last_run_time   = NOW(), " +
            "  last_run_status = $1, " +
            "  last_run_message= $2, " +
            "  run_count       = run_count + 1, " +
            "  updated_at      = NOW() " +
            "WHERE id = $3";

        DatabaseVerticle.query(vertx, sql, Tuple.tuple()
            .addString(status)
            .addString(message != null ? message : "")
            .addLong(taskId))
            .onFailure(err ->
                LOG.error("[SCHEDULER] Failed to update task {} record: {}", taskId, err.getMessage()));
    }
}
