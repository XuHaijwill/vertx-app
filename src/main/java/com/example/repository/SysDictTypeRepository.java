package com.example.repository;

import com.example.db.DatabaseVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.util.List;

/**
 * SysDictType Repository - Database operations for sys_dict_type table
 */
public class SysDictTypeRepository {

    private final Vertx vertx;

    public SysDictTypeRepository(Vertx vertx) {
        this.vertx = vertx;
    }

    // ================================================================
    // QUERY OPERATIONS
    // ================================================================

    /**
     * Find all dictionary types
     */
    public Future<List<JsonObject>> findAll() {
        String sql = "SELECT * FROM sys_dict_type ORDER BY dict_id";
        return DatabaseVerticle.query(vertx, sql)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Find dictionary type by ID
     */
    public Future<JsonObject> findById(Long dictId) {
        String sql = "SELECT * FROM sys_dict_type WHERE dict_id = $1";
        Tuple params = Tuple.tuple().addLong(dictId);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    /**
     * Find dictionary type by dict_type (unique key)
     */
    public Future<JsonObject> findByDictType(String dictType) {
        String sql = "SELECT * FROM sys_dict_type WHERE dict_type = $1";
        Tuple params = Tuple.tuple().addString(dictType);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    /**
     * Search dictionary types by name (fuzzy)
     */
    public Future<List<JsonObject>> findByDictName(String dictName) {
        if (dictName == null || dictName.isEmpty()) {
            return findAll();
        }
        String sql = "SELECT * FROM sys_dict_type WHERE dict_name LIKE $1 ORDER BY dict_id";
        Tuple params = Tuple.tuple().addString("%" + dictName + "%");
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Find dictionary types by status
     */
    public Future<List<JsonObject>> findByStatus(String status) {
        String sql = "SELECT * FROM sys_dict_type WHERE status = $1 ORDER BY dict_id";
        Tuple params = Tuple.tuple().addString(status);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Advanced search with multiple filters
     */
    public Future<List<JsonObject>> search(String dictName, String dictType, String status) {
        StringBuilder sql = new StringBuilder("SELECT * FROM sys_dict_type WHERE 1=1");
        Tuple params = Tuple.tuple();
        int paramIndex = 1;

        if (dictName != null && !dictName.isEmpty()) {
            sql.append(" AND dict_name LIKE $").append(paramIndex++);
            params.addString("%" + dictName + "%");
        }
        if (dictType != null && !dictType.isEmpty()) {
            sql.append(" AND dict_type LIKE $").append(paramIndex++);
            params.addString("%" + dictType + "%");
        }
        if (status != null && !status.isEmpty()) {
            sql.append(" AND status = $").append(paramIndex++);
            params.addString(status);
        }
        sql.append(" ORDER BY dict_id");

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Count all dictionary types
     */
    public Future<Long> count() {
        String sql = "SELECT COUNT(*) as count FROM sys_dict_type";
        return DatabaseVerticle.query(vertx, sql)
            .map(rows -> rows.iterator().next().getLong("count"));
    }

    /**
     * Check if dict_type exists
     */
    public Future<Boolean> existsByDictType(String dictType) {
        String sql = "SELECT COUNT(*) as count FROM sys_dict_type WHERE dict_type = $1";
        Tuple params = Tuple.tuple().addString(dictType);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getLong("count") > 0);
    }

    /**
     * Check if dict_type exists for other IDs (for update validation)
     */
    public Future<Boolean> existsByDictTypeExcludeId(String dictType, Long excludeId) {
        String sql = "SELECT COUNT(*) as count FROM sys_dict_type WHERE dict_type = $1 AND dict_id != $2";
        Tuple params = Tuple.tuple().addString(dictType).addLong(excludeId);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getLong("count") > 0);
    }

    // ================================================================
    // MUTATION OPERATIONS
    // ================================================================

    /**
     * Create a new dictionary type
     */
    public Future<JsonObject> create(JsonObject dict) {
        String sql = """
            INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, remark)
            VALUES ($1, $2, $3, $4, $5)
            RETURNING *
            """;
        Tuple params = Tuple.tuple()
            .addString(dict.getString("dictName"))
            .addString(dict.getString("dictType"))
            .addString(dict.getString("status", "0"))
            .addString(dict.getString("createBy", "admin"))
            .addString(dict.getString("remark"));
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    /**
     * Update an existing dictionary type
     */
    public Future<JsonObject> update(Long dictId, JsonObject dict) {
        String sql = """
            UPDATE sys_dict_type
            SET dict_name = COALESCE($2, dict_name),
                dict_type = COALESCE($3, dict_type),
                status = COALESCE($4, status),
                update_by = COALESCE($5, update_by),
                remark = COALESCE($6, remark),
                update_time = CURRENT_TIMESTAMP
            WHERE dict_id = $1
            RETURNING *
            """;
        Tuple params = Tuple.tuple()
            .addLong(dictId)
            .addString(dict.getString("dictName"))
            .addString(dict.getString("dictType"))
            .addString(dict.getString("status"))
            .addString(dict.getString("updateBy"))
            .addString(dict.getString("remark"));
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    /**
     * Delete a dictionary type by ID
     */
    public Future<Boolean> delete(Long dictId) {
        String sql = "DELETE FROM sys_dict_type WHERE dict_id = $1 RETURNING dict_id";
        Tuple params = Tuple.tuple().addLong(dictId);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.rowCount() > 0);
    }

    /**
     * Delete multiple dictionary types by IDs
     */
    public Future<Integer> deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Future.succeededFuture(0);
        String placeholders = ids.stream().map(i -> "$" + (ids.indexOf(i) + 1)).collect(java.util.stream.Collectors.joining(","));
        String sql = "DELETE FROM sys_dict_type WHERE dict_id IN (" + placeholders + ")";
        Tuple params = Tuple.tuple();
        for (Long id : ids) params.addLong(id);
        return DatabaseVerticle.query(vertx, sql, params).map(rows -> rows.rowCount());
    }

    // ================================================================
    // PAGINATION
    // ================================================================

    /**
     * Find with pagination
     */
    public Future<List<JsonObject>> findPaginated(int page, int size) {
        int offset = (page - 1) * size;
        String sql = "SELECT * FROM sys_dict_type ORDER BY dict_id LIMIT $1 OFFSET $2";
        Tuple params = Tuple.tuple().addInteger(size).addInteger(offset);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Advanced search with pagination
     */
    public Future<List<JsonObject>> searchPaginated(String dictName, String dictType, 
                                                     String status, int page, int size) {
        int offset = (page - 1) * size;
        StringBuilder sql = new StringBuilder("SELECT * FROM sys_dict_type WHERE 1=1");
        Tuple params = Tuple.tuple();
        int paramIndex = 1;

        if (dictName != null && !dictName.isEmpty()) {
            sql.append(" AND dict_name LIKE $").append(paramIndex++);
            params.addString("%" + dictName + "%");
        }
        if (dictType != null && !dictType.isEmpty()) {
            sql.append(" AND dict_type LIKE $").append(paramIndex++);
            params.addString("%" + dictType + "%");
        }
        if (status != null && !status.isEmpty()) {
            sql.append(" AND status = $").append(paramIndex++);
            params.addString(status);
        }
        sql.append(" ORDER BY dict_id LIMIT $").append(paramIndex++).append(" OFFSET $").append(paramIndex);
        params.addInteger(size).addInteger(offset);

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Count search results (for pagination total)
     */
    public Future<Long> searchCount(String dictName, String dictType, String status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) as count FROM sys_dict_type WHERE 1=1");
        Tuple params = Tuple.tuple();
        int paramIndex = 1;

        if (dictName != null && !dictName.isEmpty()) {
            sql.append(" AND dict_name LIKE $").append(paramIndex++);
            params.addString("%" + dictName + "%");
        }
        if (dictType != null && !dictType.isEmpty()) {
            sql.append(" AND dict_type LIKE $").append(paramIndex++);
            params.addString("%" + dictType + "%");
        }
        if (status != null && !status.isEmpty()) {
            sql.append(" AND status = $").append(paramIndex++);
            params.addString(status);
        }

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(rows -> rows.iterator().next().getLong("count"));
    }
}
