package com.example.db;

import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AuditLogger — audit log write engine.
 *
 * <p>Two write modes:
 * <ul>
 *   <li><b>In-tx mode</b> ({@link #logInTx}): embeds INSERT into the current
 *       active transaction via {@link TxContextHolder}; rollback is automatic
 *       — best for critical operations (order/payment creation)</li>
 *   <li><b>Standalone mode</b> ({@link #log}): owns a connection, auto-commits;
 *       audit failure does NOT affect the main business — best for
 *       config/status changes where you don't want audit to block the operation</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * // Inside a @Transactional method — embed audit into the same tx
 * {@literal @}Transactional
 * public Future&lt;Void&gt; createOrder(...) {
 *     return orderRepo.insertOrder(...)
 *         .compose(id -> audit.logInTx(
 *             TxContextHolder.current(),   // gets current tx context
 *             AuditAction.AUDIT_CREATE, "orders", id, null, orderJson))
 *         .mapEmpty();
 * }
 *
 * // Outside a tx — fire-and-forget (non-critical)
 * audit.logAsync(AuditAction.AUDIT_UPDATE, "sys_configs", id, oldCfg, newCfg);
 * </pre>
 *
 * @see AuditContextHolder
 * @see AuditContext
 */
public class AuditLogger {

    private static final Logger LOG = LoggerFactory.getLogger(AuditLogger.class);

    private static final String INSERT_SQL = """
        INSERT INTO audit_logs (
            trace_id, action, entity_type, entity_id,
            old_value, new_value, changed_fields,
            user_id, username, user_ip, user_agent, request_id,
            service_name, duration_ms, status, error_message, extra
        ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17)
        RETURNING id
        """;

    private final Vertx vertx;

    public AuditLogger(Vertx vertx) {
        this.vertx = vertx;
    }

    // ================================================================
    // In-Tx mode — embed into current @Transactional tx (RECOMMENDED for critical ops)
    // ================================================================

    /**
     * Write audit log embedded in the current active transaction.
     *
     * <p>The audit record is in the same DB transaction as the main operation.
     * If the main operation rolls back, the audit log rolls back too (strong consistency).
     *
     * <p>Requires an active {@link TxContextHolder} context (must be called from
     * inside a {@code @Transactional} method).
     *
     * @param tx          current transaction context (use {@code TxContextHolder.current()})
     * @param action      AUDIT_CREATE / AUDIT_UPDATE / AUDIT_DELETE
     * @param entityType  e.g. "orders", "products", "payments"
     * @param entityId    primary key of the affected entity
     * @param oldValue    pre-change state (null for CREATE)
     * @param newValue    post-change state (null for DELETE)
     * @return Future with the generated BIGSERIAL log ID
     */
    public Future<Long> logInTx(TransactionContext tx,
                                  AuditAction action,
                                  String entityType,
                                  String entityId,
                                  JsonObject oldValue,
                                  JsonObject newValue) {
        return logInTx(tx, action, entityType, entityId, oldValue, newValue, null, null);
    }

    /**
     * Full-parameter version.
     */
    public Future<Long> logInTx(TransactionContext tx,
                                  AuditAction action,
                                  String entityType,
                                  String entityId,
                                  JsonObject oldValue,
                                  JsonObject newValue,
                                  JsonObject extra,
                                  String errorMessage) {
        tx.tick();
        long startedAt = System.currentTimeMillis();
        JsonObject changedFields = diff(oldValue, newValue);
        AuditContext ctx = AuditContextHolder.currentOrNew();
        return doInsert(tx.conn(), startedAt, action, entityType, entityId,
            oldValue, newValue, changedFields, ctx, extra, errorMessage);
    }

    // ================================================================
    // Standalone mode — own connection, auto-commit (for non-critical ops)
    // ================================================================

    /**
     * Write audit log in an independent connection.
     *
     * <p>The audit record commits independently. If it fails, the main business
     * operation is unaffected (best-effort audit). Use this for status changes,
     * config updates, etc. where you don't want audit failure to block the request.
     */
    public Future<Void> log(AuditAction action,
                              String entityType,
                              String entityId,
                              JsonObject oldValue,
                              JsonObject newValue) {
        return log(action, entityType, entityId, oldValue, newValue, null, null);
    }

    /**
     * Full-parameter standalone version.
     */
    public Future<Void> log(AuditAction action,
                              String entityType,
                              String entityId,
                              JsonObject oldValue,
                              JsonObject newValue,
                              JsonObject extra,
                              String errorMessage) {
        long startedAt = System.currentTimeMillis();
        JsonObject changedFields = diff(oldValue, newValue);
        AuditContext ctx = AuditContextHolder.currentOrNew();
        
        Pool pool = DatabaseVerticle.getPool(vertx);
        if (pool == null) {
            return Future.failedFuture("Database not available (demo mode)");
        }
        
        Promise<Void> promise = Promise.promise();
        
        pool.getConnection()
            .onSuccess(conn -> {
                doInsert(conn, startedAt, action, entityType, entityId,
                    oldValue, newValue, changedFields, ctx, extra, errorMessage)
                    .onComplete(ar -> {
                        conn.close();
                        if (ar.succeeded()) {
                            promise.complete();
                        } else {
                            promise.fail(ar.cause());
                        }
                    });
            })
            .onFailure(promise::fail);
        
        return promise.future();
    }

    /**
     * Async fire-and-forget. Logs failure as WARN but never blocks the caller.
     *
     * <pre>
     * audit.logAsync(AuditAction.AUDIT_UPDATE, "sys_configs", id, oldCfg, newCfg);
     * </pre>
     */
    public void logAsync(AuditAction action,
                          String entityType,
                          String entityId,
                          JsonObject oldValue,
                          JsonObject newValue) {
        log(action, entityType, entityId, oldValue, newValue)
            .onFailure(e -> LOG.warn("[AUDIT] log failed: {} {} id={} — {}",
                action.value(), entityType, entityId, e.getMessage()));
    }

    // ================================================================
    // Internal — INSERT execution
    // ================================================================

    private Future<Long> doInsert(SqlConnection conn,
                                    long startedAt,
                                    AuditAction action,
                                    String entityType,
                                    String entityId,
                                    JsonObject oldValue,
                                    JsonObject newValue,
                                    JsonObject changedFields,
                                    AuditContext ctx,
                                    JsonObject extra,
                                    String errorMessage) {
        long durationMs = System.currentTimeMillis() - startedAt;
        String status = (errorMessage != null) ? "FAILURE" : "SUCCESS";

        // Merge caller extra with context extra
        JsonObject mergedExtra = (extra != null) ? extra.copy() : new JsonObject();
        if (ctx != null && ctx.getExtra() != null) {
            for (String k : ctx.getExtra().fieldNames()) {
                if (!mergedExtra.containsKey(k)) mergedExtra.put(k, ctx.getExtra().getValue(k));
            }
        }

        Tuple params = Tuple.tuple()
            .addString(ctx.getTraceId())
            .addString(action.value())
            .addString(entityType)
            .addString(String.valueOf(entityId))
            .addValue(oldValue)
            .addValue(newValue)
            .addValue(!changedFields.isEmpty() ? changedFields : null)
            .addValue(ctx.getUserId())
            .addString(ctx.getUsername())
            .addString(ctx.getUserIp())
            .addString(ctx.getUserAgent())
            .addString(ctx.getRequestId())
            .addString(ctx.getServiceName())
            .addInteger((int) durationMs)
            .addString(status)
            .addString(errorMessage)
            .addValue(!mergedExtra.isEmpty() ? mergedExtra : null);

        return conn.preparedQuery(INSERT_SQL)
            .execute(params)
            .map(rows -> {
                long id = 0;
                for (var row : rows) { id = row.getLong("id"); break; }
                LOG.debug("[AUDIT] id={} {} {} entity={} status={} duration={}ms",
                    id, action.value(), entityType, entityId, status, durationMs);
                return id;
            });
    }

    // ================================================================
    // Diff computation
    // ================================================================

    /**
     * Compute changed fields between old and new state.
     * Returns: { fieldName: {old: val, new: val}, ... }
     * Ignores created_at / updated_at / created_by / updated_by.
     * Uses 1% float tolerance for numeric comparisons.
     */
    public static JsonObject diff(JsonObject oldValue, JsonObject newValue) {
        JsonObject diff = new JsonObject();
        if (oldValue == null && newValue == null) return diff;

        if (oldValue == null) {
            for (String key : newValue.fieldNames()) {
                if (!isAuditField(key) && newValue.getValue(key) != null)
                    diff.put(key, new JsonObject().put("old", null).put("new", newValue.getValue(key)));
            }
            return diff;
        }
        if (newValue == null) {
            for (String key : oldValue.fieldNames()) {
                if (!isAuditField(key) && oldValue.getValue(key) != null)
                    diff.put(key, new JsonObject().put("old", oldValue.getValue(key)).put("new", null));
            }
            return diff;
        }

        // UPDATE: diff
        for (String key : newValue.fieldNames()) {
            if (isAuditField(key)) continue;
            Object oldVal = oldValue.getValue(key);
            Object newVal = newValue.getValue(key);
            if (!oldValue.containsKey(key)) {
                diff.put(key, new JsonObject().put("old", null).put("new", newVal));
            } else if (!equalsWithFloatTolerance(oldVal, newVal)) {
                diff.put(key, new JsonObject().put("old", oldVal).put("new", newVal));
            }
        }
        // Deleted field (old has, new doesn't)
        for (String key : oldValue.fieldNames()) {
            if (diff.containsKey(key) || isAuditField(key)) continue;
            if (!newValue.containsKey(key)) {
                diff.put(key, new JsonObject().put("old", oldValue.getValue(key)).put("new", null));
            }
        }
        return diff;
    }

    private static boolean isAuditField(String key) {
        return key.equalsIgnoreCase("created_at")
            || key.equalsIgnoreCase("updated_at")
            || key.equalsIgnoreCase("created_by")
            || key.equalsIgnoreCase("updated_by");
    }

    private static boolean equalsWithFloatTolerance(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        if (a instanceof Number && b instanceof Number) {
            double da = ((Number) a).doubleValue();
            double db = ((Number) b).doubleValue();
            if (da == 0 && db == 0) return true;
            return Math.abs(da - db) <= Math.max(Math.abs(da), Math.abs(db)) * 0.01;
        }
        return false;
    }
}
