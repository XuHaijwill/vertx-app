package com.example.repository;

import com.example.db.DatabaseVerticle;
import com.example.entity.AuditLog;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

import java.util.List;

/**
 * AuditRepository — read audit logs (write is handled by {@link com.example.db.AuditLogger}).
 *
 * <p>All methods use the connection pool (no transaction needed for read-only queries).
 */
public class AuditRepository {

    private final Vertx vertx;

    public AuditRepository(Vertx vertx) {
        this.vertx = vertx;
    }

    // ================================================================
    // Query by entity
    // ================================================================

    /**
     * Get audit history for a specific entity (most recent first).
     *
     * @param entityType  e.g. "orders", "products"
     * @param entityId    primary key
     * @param limit       max rows (1–200)
     */
    public Future<List<AuditLog>> findByEntity(String entityType, String entityId, int limit) {
        String sql = """
            SELECT * FROM audit_logs
            WHERE entity_type = $1 AND entity_id = $2
            ORDER BY created_at DESC
            LIMIT $3
            """;
        Tuple params = Tuple.tuple().addString(entityType).addString(String.valueOf(entityId)).addInteger(limit);
        return DatabaseVerticle.query(vertx, sql, params).map(AuditLog::toList);
    }

    /**
     * Get audit history for a specific user.
     */
    public Future<List<AuditLog>> findByUser(Long userId, int limit) {
        String sql = """
            SELECT * FROM audit_logs
            WHERE user_id = $1
            ORDER BY created_at DESC
            LIMIT $2
            """;
        Tuple params = Tuple.tuple().addLong(userId).addInteger(limit);
        return DatabaseVerticle.query(vertx, sql, params).map(AuditLog::toList);
    }

    /**
     * Get audit history by action type.
     */
    public Future<List<AuditLog>> findByAction(String action, int limit) {
        String sql = """
            SELECT * FROM audit_logs
            WHERE action = $1
            ORDER BY created_at DESC
            LIMIT $2
            """;
        Tuple params = Tuple.tuple().addString(action).addInteger(limit);
        return DatabaseVerticle.query(vertx, sql, params).map(AuditLog::toList);
    }

    // ================================================================
    // Search with filters
    // ================================================================

    /**
     * Search audit logs with multiple filters (all optional, AND logic).
     *
     * <p>Example — search for all DELETE operations on orders in the last 24h:
     * <pre>
     * search("orders", null, null, "AUDIT_DELETE", null, null, "2026-04-29", "2026-04-30", 1, 50);
     * </pre>
     *
     * @param entityType   filter by entity type (null = any)
     * @param entityId     filter by entity ID (null = any)
     * @param userId       filter by user ID (null = any)
     * @param action       filter by action (null = any)
     * @param status       filter by SUCCESS/FAILURE (null = any)
     * @param username     filter by username pattern (null = any)
     * @param from         ISO date string inclusive start (null = no lower bound)
     * @param to           ISO date string inclusive end (null = no upper bound)
     * @param page         1-based page number
     * @param size         page size (max 200)
     */
    public Future<List<AuditLog>> search(String entityType, String entityId,
                                            Long userId, String action, String status,
                                            String username,
                                            String from, String to,
                                            int page, int size) {
        StringBuilder sql = new StringBuilder("SELECT * FROM audit_logs WHERE 1=1");
        Tuple params = Tuple.tuple();
        int idx = 1;

        if (entityType != null && !entityType.isBlank()) {
            sql.append(" AND entity_type = $").append(idx++);
            params.addString(entityType);
        }
        if (entityId != null && !entityId.isBlank()) {
            sql.append(" AND entity_id = $").append(idx++);
            params.addString(entityId);
        }
        if (userId != null) {
            sql.append(" AND user_id = $").append(idx++);
            params.addLong(userId);
        }
        if (action != null && !action.isBlank()) {
            sql.append(" AND action = $").append(idx++);
            params.addString(action);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = $").append(idx++);
            params.addString(status);
        }
        if (username != null && !username.isBlank()) {
            sql.append(" AND username ILIKE $").append(idx++);
            params.addString("%" + username + "%");
        }
        if (from != null && !from.isBlank()) {
            sql.append(" AND created_at >= $").append(idx++);
            params.addString(from + "T00:00:00Z");
        }
        if (to != null && !to.isBlank()) {
            sql.append(" AND created_at <= $").append(idx++);
            params.addString(to + "T23:59:59Z");
        }

        sql.append(" ORDER BY created_at DESC");
        int offset = (page - 1) * size;
        sql.append(" LIMIT $").append(idx++).append(" OFFSET $").append(idx);
        params.addInteger(size).addInteger(offset);

        return DatabaseVerticle.query(vertx, sql.toString(), params).map(AuditLog::toList);
    }

    /**
     * Count results for the same filters as {@link #search}.
     */
    public Future<Long> searchCount(String entityType, String entityId,
                                      Long userId, String action, String status,
                                      String username,
                                      String from, String to) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) as count FROM audit_logs WHERE 1=1");
        Tuple params = Tuple.tuple();
        int idx = 1;

        if (entityType != null && !entityType.isBlank()) {
            sql.append(" AND entity_type = $").append(idx++);
            params.addString(entityType);
        }
        if (entityId != null && !entityId.isBlank()) {
            sql.append(" AND entity_id = $").append(idx++);
            params.addString(entityId);
        }
        if (userId != null) {
            sql.append(" AND user_id = $").append(idx++);
            params.addLong(userId);
        }
        if (action != null && !action.isBlank()) {
            sql.append(" AND action = $").append(idx++);
            params.addString(action);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = $").append(idx++);
            params.addString(status);
        }
        if (username != null && !username.isBlank()) {
            sql.append(" AND username ILIKE $").append(idx++);
            params.addString("%" + username + "%");
        }
        if (from != null && !from.isBlank()) {
            sql.append(" AND created_at >= $").append(idx++);
            params.addString(from + "T00:00:00Z");
        }
        if (to != null && !to.isBlank()) {
            sql.append(" AND created_at <= $").append(idx++);
            params.addString(to + "T23:59:59Z");
        }

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(rows -> rows.iterator().next().getLong("count"));
    }

    // ================================================================
    // Single record
    // ================================================================

    public Future<AuditLog> findById(Long id) {
        String sql = "SELECT * FROM audit_logs WHERE id = $1";
        Tuple params = Tuple.tuple().addLong(id);
        return DatabaseVerticle.query(vertx, sql, params).map(AuditLog::toOne);
    }

    /**
     * Count total audit log records.
     */
    public Future<Long> count() {
        return DatabaseVerticle.query(vertx, "SELECT COUNT(*) as count FROM audit_logs")
            .map(rows -> rows.iterator().next().getLong("count"));
    }

    // ================================================================
    // Archive operations
    // ================================================================

    /**
     * Get archive statistics: counts for active and archived tables.
     */
    public Future<JsonObject> getArchiveStats() {
        String sql = """
            SELECT
                (SELECT COUNT(*) FROM audit_logs) AS active_count,
                (SELECT COUNT(*) FROM audit_logs_archive) AS archived_count,
                (SELECT MIN(created_at) FROM audit_logs) AS oldest_active,
                (SELECT MIN(created_at) FROM audit_logs_archive) AS oldest_archived,
                (SELECT COUNT(*) FROM audit_logs WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '6 months') AS eligible_for_archive
            """;
        return DatabaseVerticle.query(vertx, sql)
            .map(rows -> {
                var row = rows.iterator().next();
                return new JsonObject()
                    .put("activeCount", row.getLong("active_count"))
                    .put("archivedCount", row.getLong("archived_count"))
                    .put("oldestActive", row.getValue("oldest_active"))
                    .put("oldestArchived", row.getValue("oldest_archived"))
                    .put("eligibleForArchive", row.getLong("eligible_for_archive"));
            });
    }

    /**
     * Archive old audit logs to audit_logs_archive table.
     *
     * @param monthsOld  archive logs older than N months (default 6)
     * @param batchSize  batch size for each iteration (default 10000)
     * @return Future with the total number of archived records
     */
    public Future<Long> archiveOldLogs(int monthsOld, int batchSize) {
        String sql = "SELECT archive_audit_logs($1, $2) AS archived_count";
        Tuple params = Tuple.tuple().addInteger(monthsOld).addInteger(batchSize);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getLong("archived_count"));
    }

    /**
     * Purge old archived logs (default: older than 3 years).
     *
     * @param yearsOld  purge archived logs older than N years
     * @return Future with the number of deleted records
     */
    public Future<Long> purgeOldArchives(int yearsOld) {
        String sql = "SELECT purge_old_audit_logs($1) AS deleted_count";
        Tuple params = Tuple.tuple().addInteger(yearsOld);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getLong("deleted_count"));
    }
}
