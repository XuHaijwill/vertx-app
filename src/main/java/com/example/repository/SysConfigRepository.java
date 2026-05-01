package com.example.repository;

import com.example.db.DatabaseVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.util.List;

/**
 * SysConfig Repository - Database operations for sys_config table
 */
public class SysConfigRepository {

    private final Vertx vertx;

    public SysConfigRepository(Vertx vertx) {
        this.vertx = vertx;
    }

    // ================================================================
    // QUERY OPERATIONS
    // ================================================================

    /**
     * Find all configurations
     */
    public Future<List<JsonObject>> findAll() {
        String sql = "SELECT * FROM sys_config ORDER BY config_id";
        return DatabaseVerticle.query(vertx, sql)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Find config by ID
     */
    public Future<JsonObject> findById(Long configId) {
        String sql = "SELECT * FROM sys_config WHERE config_id = $1";
        Tuple params = Tuple.tuple().addLong(configId);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    /**
     * Find configs by config_name (supports fuzzy search)
     */
    public Future<List<JsonObject>> findByConfigName(String configName) {
        if (configName == null || configName.isEmpty()) {
            return findAll();
        }
        String sql = "SELECT * FROM sys_config WHERE config_name LIKE $1 ORDER BY config_id";
        Tuple params = Tuple.tuple().addString("%" + configName + "%");
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Find configs by config_value (supports fuzzy search)
     */
    public Future<List<JsonObject>> findByConfigValue(String configValue) {
        if (configValue == null || configValue.isEmpty()) {
            return findAll();
        }
        String sql = "SELECT * FROM sys_config WHERE config_value LIKE $1 ORDER BY config_id";
        Tuple params = Tuple.tuple().addString("%" + configValue + "%");
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Find config by config_key (exact match)
     */
    public Future<JsonObject> findByConfigKey(String configKey) {
        String sql = "SELECT * FROM sys_config WHERE config_key = $1";
        Tuple params = Tuple.tuple().addString(configKey);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    /**
     * Find configs by config_type
     */
    public Future<List<JsonObject>> findByConfigType(String configType) {
        if (configType == null || configType.isEmpty()) {
            return findAll();
        }
        String sql = "SELECT * FROM sys_config WHERE config_type = $1 ORDER BY config_id";
        Tuple params = Tuple.tuple().addString(configType);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Advanced search with multiple filters
     */
    public Future<List<JsonObject>> search(String configName, String configKey, 
                                     String configValue, String configType) {
        StringBuilder sql = new StringBuilder("SELECT * FROM sys_config WHERE 1=1");
        Tuple params = Tuple.tuple();
        int paramIndex = 1;

        if (configName != null && !configName.isEmpty()) {
            sql.append(" AND config_name LIKE $").append(paramIndex++);
            params.addString("%" + configName + "%");
        }
        if (configKey != null && !configKey.isEmpty()) {
            sql.append(" AND config_key = $").append(paramIndex++);
            params.addString(configKey);
        }
        if (configValue != null && !configValue.isEmpty()) {
            sql.append(" AND config_value LIKE $").append(paramIndex++);
            params.addString("%" + configValue + "%");
        }
        if (configType != null && !configType.isEmpty()) {
            sql.append(" AND config_type = $").append(paramIndex++);
            params.addString(configType);
        }
        sql.append(" ORDER BY config_id");

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Count all configs
     */
    public Future<Long> count() {
        String sql = "SELECT COUNT(*) as count FROM sys_config";
        return DatabaseVerticle.query(vertx, sql)
            .map(rows -> {
                Row row = rows.iterator().next();
                return row.getLong("count");
            });
    }

    /**
     * Count configs by config_name
     */
    public Future<Long> countByConfigName(String configName) {
        String sql = "SELECT COUNT(*) as count FROM sys_config WHERE config_name LIKE $1";
        Tuple params = Tuple.tuple().addString("%" + configName + "%");
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getLong("count"));
    }

    // ================================================================
    // PAGINATION
    // ================================================================

    /**
     * Find with pagination
     */
    public Future<List<JsonObject>> findPaginated(int page, int size) {
        int offset = (page - 1) * size;
        String sql = "SELECT * FROM sys_config ORDER BY config_id LIMIT $1 OFFSET $2";
        Tuple params = Tuple.tuple().addInteger(size).addInteger(offset);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Advanced search with pagination
     */
    public Future<List<JsonObject>> searchPaginated(String configName, String configKey,
                                              String configValue, String configType,
                                              int page, int size) {
        int offset = (page - 1) * size;
        StringBuilder sql = new StringBuilder("SELECT * FROM sys_config WHERE 1=1");
        Tuple params = Tuple.tuple();
        int paramIndex = 1;

        if (configName != null && !configName.isEmpty()) {
            sql.append(" AND config_name LIKE $").append(paramIndex++);
            params.addString("%" + configName + "%");
        }
        if (configKey != null && !configKey.isEmpty()) {
            sql.append(" AND config_key = $").append(paramIndex++);
            params.addString(configKey);
        }
        if (configValue != null && !configValue.isEmpty()) {
            sql.append(" AND config_value LIKE $").append(paramIndex++);
            params.addString("%" + configValue + "%");
        }
        if (configType != null && !configType.isEmpty()) {
            sql.append(" AND config_type = $").append(paramIndex++);
            params.addString(configType);
        }
        sql.append(" ORDER BY config_id LIMIT $").append(paramIndex++).append(" OFFSET $").append(paramIndex);
        params.addInteger(size).addInteger(offset);

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Count search results (for pagination total)
     */
    public Future<Long> searchCount(String configName, String configKey,
                                    String configValue, String configType) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) as count FROM sys_config WHERE 1=1");
        Tuple params = Tuple.tuple();
        int paramIndex = 1;

        if (configName != null && !configName.isEmpty()) {
            sql.append(" AND config_name LIKE $").append(paramIndex++);
            params.addString("%" + configName + "%");
        }
        if (configKey != null && !configKey.isEmpty()) {
            sql.append(" AND config_key = $").append(paramIndex++);
            params.addString(configKey);
        }
        if (configValue != null && !configValue.isEmpty()) {
            sql.append(" AND config_value LIKE $").append(paramIndex++);
            params.addString("%" + configValue + "%");
        }
        if (configType != null && !configType.isEmpty()) {
            sql.append(" AND config_type = $").append(paramIndex++);
            params.addString(configType);
        }

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(rows -> rows.iterator().next().getLong("count"));
    }
}
