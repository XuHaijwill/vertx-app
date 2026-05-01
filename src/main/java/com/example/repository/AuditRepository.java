package com.example.repository;

import com.example.db.DatabaseVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
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
    public Future<List<JsonObject>> findByEntity(String entityType, String entityId, int limit) {
        String sql = """
            SELECT * FROM audit_logs
            WHERE entity_type = $1 AND entity_id = $2
            ORDER BY created_at DESC
            LIMIT $3
            """;
        Tuple params = Tuple.tuple().addString(entityType).addString(String.valueOf(entityId)).addInteger(limit);
        return DatabaseVerticle.query(vertx, sql, params).map(DatabaseVerticle::toJsonList);
    }

    /**
     * Get audit history for a specific user.
     */
    public Future<List<JsonObject>> findByUser(Long userId, int limit) {
        String sql = """
            SELECT * FROM audit_logs
            WHERE user_id = $1
            ORDER BY created_at DESC
            LIMIT $2
            """;
        Tuple params = Tuple.tuple().addLong(userId).addInteger(limit);
        return DatabaseVerticle.query(vertx, sql, params).map(DatabaseVerticle::toJsonList);
    }

    /**
     * Get audit history by action type.
     */
    public Future<List<JsonObject>> findByAction(String action, int limit) {
        String sql = """
            SELECT * FROM audit_logs
            WHERE action = $1
            ORDER BY created_at DESC
            LIMIT $2
            """;
        Tuple params = Tuple.tuple().addString(action).addInteger(limit);
        return DatabaseVerticle.query(vertx, sql, params).map(DatabaseVerticle::toJsonList);
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
    public Future<List<JsonObject>> search(String entityType, String entityId,
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

        return DatabaseVerticle.query(vertx, sql.toString(), params).map(DatabaseVerticle::toJsonList);
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

    public Future<JsonObject> findById(Long id) {
        String sql = "SELECT * FROM audit_logs WHERE id = $1";
        Tuple params = Tuple.tuple().addLong(id);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    /**
     * Count total audit log records.
     */
    public Future<Long> count() {
        return DatabaseVerticle.query(vertx, "SELECT COUNT(*) as count FROM audit_logs")
            .map(rows -> rows.iterator().next().getLong("count"));
    }
}
