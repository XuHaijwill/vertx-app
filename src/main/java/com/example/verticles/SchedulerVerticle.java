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
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
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
 *   <li>Supports task types: HTTP, SQL, CLASS (Java method invocation)</li>
 *   <li>Updates last_run_time, last_run_status, last_run_message, run_count,
 *       next_run_time (computed from cron expression) after each execution</li>
 *   <li>Uses a ConcurrentHashMap to track currently-running tasks (prevent concurrent runs
 *       of the same task)</li>
 * </ul>
 *
 * <h2>Task Types:</h2>
 *
 * <h3>1. HTTP Task</h3>
 * <pre>
 * INSERT INTO scheduled_tasks (name, task_type, config, cron, next_run_time, status)
 * VALUES ('ping-api', 'HTTP',
 *   '{"url": "http://localhost:8888/api/health", "method": "GET", "headers": {"Authorization": "Bearer xxx"}}',
 *   '0 0 * * * ?',
 *   CURRENT_TIMESTAMP,
 *   'ACTIVE');
 * </pre>
 *
 * <h3>2. SQL Task</h3>
 * <pre>
 * INSERT INTO scheduled_tasks (name, task_type, config, cron, next_run_time, status)
 * VALUES ('cleanup-old-logs', 'SQL',
 *   '{"sql": "DELETE FROM logs WHERE created_at < NOW() - INTERVAL ''30 days''"}',
 *   '0 0 3 * * ?',  -- 每天 3:00 执行
 *   CURRENT_TIMESTAMP,
 *   'ACTIVE');
 * </pre>
 *
 * <h3>3. CLASS Task (Java Method Invocation)</h3>
 * <pre>
 * -- 同步方法示例
 * INSERT INTO scheduled_tasks (name, task_type, config, cron, next_run_time, status)
 * VALUES ('daily-report', 'CLASS',
 *   '{"class": "com.example.tasks.ReportTasks", "method": "generateDailyReport", "params": {"type": "sales"}}',
 *   '0 0 8 * * ?',  -- 每天 8:00 执行
 *   CURRENT_TIMESTAMP,
 *   'ACTIVE');
 *
 * -- 异步 Vert.x Future 方法示例
 * INSERT INTO scheduled_tasks (name, task_type, config, cron, next_run_time, status)
 * VALUES ('sync-inventory', 'CLASS',
 *   '{"class": "com.example.tasks.InventoryTasks", "method": "syncFromExternal", "async": true}',
 *   '0 30 * * * ?',  -- 每小时 30 分执行
 *   CURRENT_TIMESTAMP,
 *   'ACTIVE');
 * </pre>
 *
 * <h2>CLASS Task Requirements:</h2>
 * <ul>
 *   <li>Method must be public static</li>
 *   <li>Sync methods: accept JsonObject param, return String</li>
 *   <li>Async methods: accept (Vertx, JsonObject) params, return Future&lt;String&gt;</li>
 *   <li>Set "async": true in config for Future-returning methods</li>
 * </ul>
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
            case "HTTP"  -> executeHttpTask(cfg);
            case "SQL"   -> executeSqlTask(cfg);
            case "CLASS" -> executeClassTask(cfg);
            default      -> Future.failedFuture("Unknown task type: " + taskType);
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

    // ---------------------------------------------------------------
    // CLASS task (Java method invocation)
    // ---------------------------------------------------------------

    /**
     * Execute a Java class method via reflection.
     *
     * <p>Config fields:</p>
     * <ul>
     *   <li>class:  fully-qualified class name (e.g., "com.example.tasks.ReportTasks")</li>
     *   <li>method: public static method name</li>
     *   <li>params: optional JsonObject passed to the method (default: empty)</li>
     *   <li>async:  if true, method returns Future&lt;String&gt;; otherwise returns String</li>
     * </ul>
     *
     * <p>Sync method signature: public static String methodName(JsonObject params)</p>
     * <p>Async method signature: public static Future&lt;String&gt; methodName(Vertx vertx, JsonObject params)</p>
     */
    @SuppressWarnings("unchecked")
    private Future<String> executeClassTask(JsonObject cfg) {
        String className  = cfg.getString("class");
        String methodName = cfg.getString("method");
        JsonObject params = cfg.getJsonObject("params", new JsonObject());
        boolean isAsync   = cfg.getBoolean("async", false);

        if (className == null || className.isBlank()) {
            return Future.failedFuture("CLASS task: 'class' is required");
        }
        if (methodName == null || methodName.isBlank()) {
            return Future.failedFuture("CLASS task: 'method' is required");
        }

        Promise<String> promise = Promise.promise();

        try {
            // Load class
            Class<?> clazz = Class.forName(className);

            if (isAsync) {
                // Async method: public static Future<String> method(Vertx, JsonObject)
                Method method = clazz.getMethod(methodName, Vertx.class, JsonObject.class);
                Object result = method.invoke(null, vertx, params);
                if (result instanceof Future) {
                    ((Future<String>) result).onComplete(ar -> {
                        if (ar.succeeded()) {
                            promise.complete(ar.result());
                        } else {
                            promise.fail(ar.cause());
                        }
                    });
                } else {
                    promise.fail("Async method must return Future<String>");
                }
            } else {
                // Sync method: public static String method(JsonObject)
                Method method = clazz.getMethod(methodName, JsonObject.class);
                Object result = method.invoke(null, params);
                if (result instanceof String) {
                    promise.complete((String) result);
                } else {
                    promise.fail("Sync method must return String");
                }
            }
        } catch (ClassNotFoundException e) {
            promise.fail("Class not found: " + className);
        } catch (NoSuchMethodException e) {
            promise.fail("Method not found: " + className + "." + methodName);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            promise.fail("Invocation error: " + cause.getMessage());
            LOG.error("[SCHEDULER] CLASS task invocation error", e);
        }

        return promise.future();
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
