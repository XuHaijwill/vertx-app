package com.example.repository;

import com.example.db.DatabaseVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.util.List;

/**
 * SysDictData Repository - Database operations for sys_dict_data table
 */
public class SysDictDataRepository {

    private final Vertx vertx;

    public SysDictDataRepository(Vertx vertx) {
        this.vertx = vertx;
    }

    // ================================================================
    // QUERY OPERATIONS
    // ================================================================

    /**
     * Find all dictionary data
     */
    public Future<List<JsonObject>> findAll() {
        String sql = "SELECT * FROM sys_dict_data ORDER BY dict_sort, dict_code";
        return DatabaseVerticle.query(vertx, sql)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Find dictionary data by code
     */
    public Future<JsonObject> findById(Long dictCode) {
        String sql = "SELECT * FROM sys_dict_data WHERE dict_code = $1";
        Tuple params = Tuple.tuple().addLong(dictCode);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    /**
     * Find all dictionary data by dict_type
     */
    public Future<List<JsonObject>> findByDictType(String dictType) {
        String sql = "SELECT * FROM sys_dict_data WHERE dict_type = $1 ORDER BY dict_sort, dict_code";
        Tuple params = Tuple.tuple().addString(dictType);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Find dictionary data by dict_type and dict_value
     */
    public Future<JsonObject> findByDictTypeAndValue(String dictType, String dictValue) {
        String sql = "SELECT * FROM sys_dict_data WHERE dict_type = $1 AND dict_value = $2";
        Tuple params = Tuple.tuple().addString(dictType).addString(dictValue);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    /**
     * Find dictionary data by label (fuzzy search)
     */
    public Future<List<JsonObject>> findByDictLabel(String dictLabel) {
        if (dictLabel == null || dictLabel.isEmpty()) {
            return findAll();
        }
        String sql = "SELECT * FROM sys_dict_data WHERE dict_label LIKE $1 ORDER BY dict_sort, dict_code";
        Tuple params = Tuple.tuple().addString("%" + dictLabel + "%");
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Find dictionary data by status
     */
    public Future<List<JsonObject>> findByStatus(String status) {
        String sql = "SELECT * FROM sys_dict_data WHERE status = $1 ORDER BY dict_sort, dict_code";
        Tuple params = Tuple.tuple().addString(status);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Find default dictionary data by dict_type
     */
    public Future<List<JsonObject>> findDefaultsByDictType(String dictType) {
        String sql = "SELECT * FROM sys_dict_data WHERE dict_type = $1 AND is_default = 'Y' ORDER BY dict_sort, dict_code";
        Tuple params = Tuple.tuple().addString(dictType);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Advanced search with multiple filters
     */
    public Future<List<JsonObject>> search(String dictType, String dictLabel, 
                                           String dictValue, String status) {
        StringBuilder sql = new StringBuilder("SELECT * FROM sys_dict_data WHERE 1=1");
        Tuple params = Tuple.tuple();
        int paramIndex = 1;

        if (dictType != null && !dictType.isEmpty()) {
            sql.append(" AND dict_type = $").append(paramIndex++);
            params.addString(dictType);
        }
        if (dictLabel != null && !dictLabel.isEmpty()) {
            sql.append(" AND dict_label LIKE $").append(paramIndex++);
            params.addString("%" + dictLabel + "%");
        }
        if (dictValue != null && !dictValue.isEmpty()) {
            sql.append(" AND dict_value LIKE $").append(paramIndex++);
            params.addString("%" + dictValue + "%");
        }
        if (status != null && !status.isEmpty()) {
            sql.append(" AND status = $").append(paramIndex++);
            params.addString(status);
        }
        sql.append(" ORDER BY dict_sort, dict_code");

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Count all dictionary data
     */
    public Future<Long> count() {
        String sql = "SELECT COUNT(*) as count FROM sys_dict_data";
        return DatabaseVerticle.query(vertx, sql)
            .map(rows -> rows.iterator().next().getLong("count"));
    }

    /**
     * Count dictionary data by dict_type
     */
    public Future<Long> countByDictType(String dictType) {
        String sql = "SELECT COUNT(*) as count FROM sys_dict_data WHERE dict_type = $1";
        Tuple params = Tuple.tuple().addString(dictType);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getLong("count"));
    }

    /**
     * Check if dict_type exists
     */
    public Future<Boolean> existsByDictType(String dictType) {
        String sql = "SELECT COUNT(*) as count FROM sys_dict_data WHERE dict_type = $1";
        Tuple params = Tuple.tuple().addString(dictType);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getLong("count") > 0);
    }

    // ================================================================
    // MUTATION OPERATIONS
    // ================================================================

    /**
     * Create new dictionary data
     */
    public Future<JsonObject> create(JsonObject dict) {
        String sql = """
            INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, 
                                       css_class, list_class, is_default, status, create_by, remark)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
            RETURNING *
            """;
        Tuple params = Tuple.tuple()
            .addInteger(dict.getInteger("dictSort", 0))
            .addString(dict.getString("dictLabel"))
            .addString(dict.getString("dictValue"))
            .addString(dict.getString("dictType"))
            .addString(dict.getString("cssClass"))
            .addString(dict.getString("listClass"))
            .addString(dict.getString("isDefault", "N"))
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
     * Update existing dictionary data
     */
    public Future<JsonObject> update(Long dictCode, JsonObject dict) {
        String sql = """
            UPDATE sys_dict_data
            SET dict_sort = COALESCE($2, dict_sort),
                dict_label = COALESCE($3, dict_label),
                dict_value = COALESCE($4, dict_value),
                dict_type = COALESCE($5, dict_type),
                css_class = COALESCE($6, css_class),
                list_class = COALESCE($7, list_class),
                is_default = COALESCE($8, is_default),
                status = COALESCE($9, status),
                update_by = COALESCE($10, update_by),
                remark = COALESCE($11, remark),
                update_time = CURRENT_TIMESTAMP
            WHERE dict_code = $1
            RETURNING *
            """;
        Tuple params = Tuple.tuple()
            .addLong(dictCode)
            .addInteger(dict.getInteger("dictSort"))
            .addString(dict.getString("dictLabel"))
            .addString(dict.getString("dictValue"))
            .addString(dict.getString("dictType"))
            .addString(dict.getString("cssClass"))
            .addString(dict.getString("listClass"))
            .addString(dict.getString("isDefault"))
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
     * Delete dictionary data by code
     */
    public Future<Boolean> delete(Long dictCode) {
        String sql = "DELETE FROM sys_dict_data WHERE dict_code = $1 RETURNING dict_code";
        Tuple params = Tuple.tuple().addLong(dictCode);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.rowCount() > 0);
    }

    /**
     * Delete multiple dictionary data by codes
     */
    public Future<Integer> deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Future.succeededFuture(0);
        String placeholders = ids.stream().map(i -> "$" + (ids.indexOf(i) + 1)).collect(java.util.stream.Collectors.joining(","));
        String sql = "DELETE FROM sys_dict_data WHERE dict_code IN (" + placeholders + ")";
        Tuple params = Tuple.tuple();
        for (Long id : ids) params.addLong(id);
        return DatabaseVerticle.query(vertx, sql, params).map(rows -> rows.rowCount());
    }

    /**
     * Delete all dictionary data by dict_type
     */
    public Future<Integer> deleteByDictType(String dictType) {
        String sql = "DELETE FROM sys_dict_data WHERE dict_type = $1";
        Tuple params = Tuple.tuple().addString(dictType);
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
        String sql = "SELECT * FROM sys_dict_data ORDER BY dict_sort, dict_code LIMIT $1 OFFSET $2";
        Tuple params = Tuple.tuple().addInteger(size).addInteger(offset);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Advanced search with pagination
     */
    public Future<List<JsonObject>> searchPaginated(String dictType, String dictLabel,
                                                     String dictValue, String status,
                                                     int page, int size) {
        int offset = (page - 1) * size;
        StringBuilder sql = new StringBuilder("SELECT * FROM sys_dict_data WHERE 1=1");
        Tuple params = Tuple.tuple();
        int paramIndex = 1;

        if (dictType != null && !dictType.isEmpty()) {
            sql.append(" AND dict_type = $").append(paramIndex++);
            params.addString(dictType);
        }
        if (dictLabel != null && !dictLabel.isEmpty()) {
            sql.append(" AND dict_label LIKE $").append(paramIndex++);
            params.addString("%" + dictLabel + "%");
        }
        if (dictValue != null && !dictValue.isEmpty()) {
            sql.append(" AND dict_value LIKE $").append(paramIndex++);
            params.addString("%" + dictValue + "%");
        }
        if (status != null && !status.isEmpty()) {
            sql.append(" AND status = $").append(paramIndex++);
            params.addString(status);
        }
        sql.append(" ORDER BY dict_sort, dict_code LIMIT $").append(paramIndex++).append(" OFFSET $").append(paramIndex);
        params.addInteger(size).addInteger(offset);

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Count search results (for pagination total)
     */
    public Future<Long> searchCount(String dictType, String dictLabel,
                                     String dictValue, String status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) as count FROM sys_dict_data WHERE 1=1");
        Tuple params = Tuple.tuple();
        int paramIndex = 1;

        if (dictType != null && !dictType.isEmpty()) {
            sql.append(" AND dict_type = $").append(paramIndex++);
            params.addString(dictType);
        }
        if (dictLabel != null && !dictLabel.isEmpty()) {
            sql.append(" AND dict_label LIKE $").append(paramIndex++);
            params.addString("%" + dictLabel + "%");
        }
        if (dictValue != null && !dictValue.isEmpty()) {
            sql.append(" AND dict_value LIKE $").append(paramIndex++);
            params.addString("%" + dictValue + "%");
        }
        if (status != null && !status.isEmpty()) {
            sql.append(" AND status = $").append(paramIndex++);
            params.addString(status);
        }

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(rows -> rows.iterator().next().getLong("count"));
    }

    // ================================================================
    // BATCH OPERATIONS
    // ================================================================

    /**
     * Create multiple dictionary data entries
     */
    public Future<List<JsonObject>> createBatch(List<JsonObject> items) {
        if (items == null || items.isEmpty()) return Future.succeededFuture(List.of());
        
        List<String> columns = List.of("dict_sort", "dict_label", "dict_value", "dict_type",
                                       "css_class", "list_class", "is_default", "status", "create_by", "remark");
        List<List<Object>> values = items.stream().map(d -> List.<Object>of(
            d.getInteger("dictSort", 0),
            d.getString("dictLabel"),
            d.getString("dictValue"),
            d.getString("dictType"),
            d.getString("cssClass"),
            d.getString("listClass"),
            d.getString("isDefault", "N"),
            d.getString("status", "0"),
            d.getString("createBy", "admin"),
            d.getString("remark")
        )).collect(java.util.stream.Collectors.toList());
        
        return com.example.db.BatchOperations.multiRowInsert(vertx, "sys_dict_data", columns, values, "dict_code")
            .compose(ids -> {
                if (ids.isEmpty()) return Future.succeededFuture(List.<JsonObject>of());
                String placeholders = ids.stream().map(i -> "$" + (ids.indexOf(i) + 1)).collect(java.util.stream.Collectors.joining(","));
                Tuple params = Tuple.tuple();
                for (Long id : ids) params.addLong(id);
                return DatabaseVerticle.query(vertx, "SELECT * FROM sys_dict_data WHERE dict_code IN (" + placeholders + ") ORDER BY dict_code", params)
                    .map(DatabaseVerticle::toJsonList);
            });
    }

    /**
     * Check if dict_data exists by dict_type and dict_value
     */
    public Future<Boolean> existsByDictTypeAndValue(String dictType, String dictValue) {
        String sql = "SELECT COUNT(*) as count FROM sys_dict_data WHERE dict_type = $1 AND dict_value = $2";
        Tuple params = Tuple.tuple().addString(dictType).addString(dictValue);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getLong("count") > 0);
    }
}
