package com.example.repository;

import com.example.db.DatabaseVerticle;
import com.example.entity.SysConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SysConfig Repository - Database operations for sys_config table
 */
public class SysConfigRepository {

    private final Vertx vertx;

    public SysConfigRepository(Vertx vertx) {
        this.vertx = vertx;
    }

    // ================================================================
    // Row → Entity converters
    // ================================================================

    private SysConfig toConfig(Row row) {
        return SysConfig.fromRow(row);
    }

    private List<SysConfig> toConfigList(RowSet<Row> rows) {
        List<SysConfig> list = new ArrayList<>();
        for (Row row : rows) {
            list.add(toConfig(row));
        }
        return list;
    }

    // ================================================================
    // QUERY OPERATIONS
    // ================================================================

    public Future<List<SysConfig>> findAll() {
        String sql = "SELECT * FROM sys_config ORDER BY config_id";
        return DatabaseVerticle.query(vertx, sql)
            .map(this::toConfigList);
    }

    public Future<SysConfig> findById(Long configId) {
        String sql = "SELECT * FROM sys_config WHERE config_id = $1";
        Tuple params = Tuple.tuple().addLong(configId);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<SysConfig> list = toConfigList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    public Future<List<SysConfig>> findByConfigName(String configName) {
        if (configName == null || configName.isEmpty()) {
            return findAll();
        }
        String sql = "SELECT * FROM sys_config WHERE config_name LIKE $1 ORDER BY config_id";
        Tuple params = Tuple.tuple().addString("%" + configName + "%");
        return DatabaseVerticle.query(vertx, sql, params)
            .map(this::toConfigList);
    }

    public Future<List<SysConfig>> findByConfigValue(String configValue) {
        if (configValue == null || configValue.isEmpty()) {
            return findAll();
        }
        String sql = "SELECT * FROM sys_config WHERE config_value LIKE $1 ORDER BY config_id";
        Tuple params = Tuple.tuple().addString("%" + configValue + "%");
        return DatabaseVerticle.query(vertx, sql, params)
            .map(this::toConfigList);
    }

    public Future<SysConfig> findByConfigKey(String configKey) {
        String sql = "SELECT * FROM sys_config WHERE config_key = $1";
        Tuple params = Tuple.tuple().addString(configKey);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<SysConfig> list = toConfigList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    public Future<List<SysConfig>> findByConfigType(String configType) {
        if (configType == null || configType.isEmpty()) {
            return findAll();
        }
        String sql = "SELECT * FROM sys_config WHERE config_type = $1 ORDER BY config_id";
        Tuple params = Tuple.tuple().addString(configType);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(this::toConfigList);
    }

    public Future<List<SysConfig>> search(String configName, String configKey,
                                           String configValue, String configType) {
        StringBuilder sql = new StringBuilder("SELECT * FROM sys_config WHERE 1=1");
        Tuple params = Tuple.tuple();
        int idx = 1;

        if (configName != null && !configName.isEmpty()) {
            sql.append(" AND config_name LIKE $").append(idx++);
            params.addString("%" + configName + "%");
        }
        if (configKey != null && !configKey.isEmpty()) {
            sql.append(" AND config_key = $").append(idx++);
            params.addString(configKey);
        }
        if (configValue != null && !configValue.isEmpty()) {
            sql.append(" AND config_value LIKE $").append(idx++);
            params.addString("%" + configValue + "%");
        }
        if (configType != null && !configType.isEmpty()) {
            sql.append(" AND config_type = $").append(idx++);
            params.addString(configType);
        }
        sql.append(" ORDER BY config_id");

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(this::toConfigList);
    }

    public Future<Long> count() {
        String sql = "SELECT COUNT(*) as cnt FROM sys_config";
        return DatabaseVerticle.query(vertx, sql)
            .map(rows -> rows.iterator().next().getLong("cnt"));
    }

    public Future<Long> countByConfigName(String configName) {
        String sql = "SELECT COUNT(*) as cnt FROM sys_config WHERE config_name LIKE $1";
        Tuple params = Tuple.tuple().addString("%" + configName + "%");
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getLong("cnt"));
    }

    public Future<Boolean> existsByConfigKey(String configKey) {
        String sql = "SELECT COUNT(*) as cnt FROM sys_config WHERE config_key = $1";
        Tuple params = Tuple.tuple().addString(configKey);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getLong("cnt") > 0);
    }

    // ================================================================
    // MUTATION OPERATIONS
    // ================================================================

    public Future<SysConfig> create(SysConfig config) {
        String sql = """
            INSERT INTO sys_config (config_key, config_name, config_value, config_type, description)
            VALUES ($1, $2, $3, $4, $5)
            RETURNING *
            """;
        Tuple params = Tuple.tuple()
            .addString(config.getConfigKey())
            .addString(config.getConfigName())
            .addString(config.getConfigValue())
            .addString(config.getConfigType())
            .addString(config.getDescription());
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<SysConfig> list = toConfigList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    public Future<SysConfig> update(Long configId, SysConfig config) {
        String sql = """
            UPDATE sys_config
            SET config_key = COALESCE($2, config_key),
                config_name = COALESCE($3, config_name),
                config_value = COALESCE($4, config_value),
                config_type = COALESCE($5, config_type),
                description = COALESCE($6, description),
                update_time = CURRENT_TIMESTAMP
            WHERE config_id = $1
            RETURNING *
            """;
        Tuple params = Tuple.tuple()
            .addLong(configId)
            .addString(config.getConfigKey())
            .addString(config.getConfigName())
            .addString(config.getConfigValue())
            .addString(config.getConfigType())
            .addString(config.getDescription());
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<SysConfig> list = toConfigList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    public Future<Void> delete(Long configId) {
        String sql = "DELETE FROM sys_config WHERE config_id = $1";
        Tuple params = Tuple.tuple().addLong(configId);
        return DatabaseVerticle.query(vertx, sql, params)
            .mapEmpty();
    }

    // ================================================================
    // PAGINATION
    // ================================================================

    public Future<List<SysConfig>> findPaginated(int page, int size) {
        int offset = (page - 1) * size;
        String sql = "SELECT * FROM sys_config ORDER BY config_id LIMIT $1 OFFSET $2";
        Tuple params = Tuple.tuple().addInteger(size).addInteger(offset);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(this::toConfigList);
    }

    public Future<List<SysConfig>> searchPaginated(String configName, String configKey,
                                              String configValue, String configType,
                                              int page, int size) {
        int offset = (page - 1) * size;
        StringBuilder sql = new StringBuilder("SELECT * FROM sys_config WHERE 1=1");
        Tuple params = Tuple.tuple();
        int idx = 1;

        if (configName != null && !configName.isEmpty()) {
            sql.append(" AND config_name LIKE $").append(idx++);
            params.addString("%" + configName + "%");
        }
        if (configKey != null && !configKey.isEmpty()) {
            sql.append(" AND config_key = $").append(idx++);
            params.addString(configKey);
        }
        if (configValue != null && !configValue.isEmpty()) {
            sql.append(" AND config_value LIKE $").append(idx++);
            params.addString("%" + configValue + "%");
        }
        if (configType != null && !configType.isEmpty()) {
            sql.append(" AND config_type = $").append(idx++);
            params.addString(configType);
        }
        sql.append(" ORDER BY config_id LIMIT $").append(idx++).append(" OFFSET $").append(idx);
        params.addInteger(size).addInteger(offset);

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(this::toConfigList);
    }

    public Future<Long> searchCount(String configName, String configKey,
                                    String configValue, String configType) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) as cnt FROM sys_config WHERE 1=1");
        Tuple params = Tuple.tuple();
        int idx = 1;

        if (configName != null && !configName.isEmpty()) {
            sql.append(" AND config_name LIKE $").append(idx++);
            params.addString("%" + configName + "%");
        }
        if (configKey != null && !configKey.isEmpty()) {
            sql.append(" AND config_key = $").append(idx++);
            params.addString(configKey);
        }
        if (configValue != null && !configValue.isEmpty()) {
            sql.append(" AND config_value LIKE $").append(idx++);
            params.addString("%" + configValue + "%");
        }
        if (configType != null && !configType.isEmpty()) {
            sql.append(" AND config_type = $").append(idx++);
            params.addString(configType);
        }

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(rows -> rows.iterator().next().getLong("cnt"));
    }
}
