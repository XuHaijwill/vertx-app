package com.example.repository;

import com.example.db.DatabaseVerticle;
import com.example.entity.SysDictType;
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
 * SysDictType Repository - Database operations for sys_dict_type table
 */
public class SysDictTypeRepository {

    private final Vertx vertx;

    public SysDictTypeRepository(Vertx vertx) {
        this.vertx = vertx;
    }

    // ================================================================
    // ROW → ENTITY HELPERS
    // ================================================================

    private List<SysDictType> toList(RowSet<Row> rows) {
        List<SysDictType> list = new ArrayList<>();
        for (Row row : rows) {
            list.add(SysDictType.toSysDictType(row));
        }
        return list;
    }

    private SysDictType toOne(RowSet<Row> rows) {
        return rows.iterator().hasNext() ? SysDictType.toSysDictType(rows.iterator().next()) : null;
    }

    // ================================================================
    // QUERY OPERATIONS
    // ================================================================

    public Future<List<SysDictType>> findAll() {
        String sql = "SELECT * FROM sys_dict_type ORDER BY dict_id";
        return DatabaseVerticle.query(vertx, sql).map(this::toList);
    }

    public Future<SysDictType> findById(Long dictId) {
        String sql = "SELECT * FROM sys_dict_type WHERE dict_id = $1";
        Tuple params = Tuple.tuple().addLong(dictId);
        return DatabaseVerticle.query(vertx, sql, params).map(this::toOne);
    }

    public Future<SysDictType> findByDictType(String dictType) {
        String sql = "SELECT * FROM sys_dict_type WHERE dict_type = $1";
        Tuple params = Tuple.tuple().addString(dictType);
        return DatabaseVerticle.query(vertx, sql, params).map(this::toOne);
    }

    public Future<List<SysDictType>> findByDictName(String dictName) {
        if (dictName == null || dictName.isEmpty()) {
            return findAll();
        }
        String sql = "SELECT * FROM sys_dict_type WHERE dict_name LIKE $1 ORDER BY dict_id";
        Tuple params = Tuple.tuple().addString("%" + dictName + "%");
        return DatabaseVerticle.query(vertx, sql, params).map(this::toList);
    }

    public Future<List<SysDictType>> findByStatus(String status) {
        String sql = "SELECT * FROM sys_dict_type WHERE status = $1 ORDER BY dict_id";
        Tuple params = Tuple.tuple().addString(status);
        return DatabaseVerticle.query(vertx, sql, params).map(this::toList);
    }

    public Future<List<SysDictType>> search(String dictName, String dictType, String status) {
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

        return DatabaseVerticle.query(vertx, sql.toString(), params).map(this::toList);
    }

    public Future<Long> count() {
        String sql = "SELECT COUNT(*) as count FROM sys_dict_type";
        return DatabaseVerticle.query(vertx, sql)
            .map(rows -> rows.iterator().next().getLong("count"));
    }

    public Future<Boolean> existsByDictType(String dictType) {
        String sql = "SELECT COUNT(*) as count FROM sys_dict_type WHERE dict_type = $1";
        Tuple params = Tuple.tuple().addString(dictType);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getLong("count") > 0);
    }

    public Future<Boolean> existsByDictTypeExcludeId(String dictType, Long excludeId) {
        String sql = "SELECT COUNT(*) as count FROM sys_dict_type WHERE dict_type = $1 AND dict_id != $2";
        Tuple params = Tuple.tuple().addString(dictType).addLong(excludeId);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getLong("count") > 0);
    }

    // ================================================================
    // MUTATION OPERATIONS
    // ================================================================

    public Future<SysDictType> create(SysDictType dict) {
        String sql = """
            INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, remark)
            VALUES ($1, $2, $3, $4, $5)
            RETURNING *
            """;
        Tuple params = Tuple.tuple()
            .addString(dict.getDictName())
            .addString(dict.getDictType())
            .addString(dict.getStatus() != null ? dict.getStatus() : "0")
            .addString(dict.getCreateBy() != null ? dict.getCreateBy() : "admin")
            .addString(dict.getRemark());
        return DatabaseVerticle.query(vertx, sql, params).map(this::toOne);
    }

    public Future<SysDictType> update(Long dictId, SysDictType dict) {
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
            .addString(dict.getDictName())
            .addString(dict.getDictType())
            .addString(dict.getStatus())
            .addString(dict.getUpdateBy())
            .addString(dict.getRemark());
        return DatabaseVerticle.query(vertx, sql, params).map(this::toOne);
    }

    public Future<Boolean> delete(Long dictId) {
        String sql = "DELETE FROM sys_dict_type WHERE dict_id = $1 RETURNING dict_id";
        Tuple params = Tuple.tuple().addLong(dictId);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.rowCount() > 0);
    }

    public Future<Integer> deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Future.succeededFuture(0);
        String placeholders = ids.stream().map(i -> "$" + (ids.indexOf(i) + 1)).collect(Collectors.joining(","));
        String sql = "DELETE FROM sys_dict_type WHERE dict_id IN (" + placeholders + ")";
        Tuple params = Tuple.tuple();
        for (Long id : ids) params.addLong(id);
        return DatabaseVerticle.query(vertx, sql, params).map(rows -> rows.rowCount());
    }

    // ================================================================
    // FILTER / UNIQUE CHECK (mirrors RuoYi SysDictTypeMapper)
    // ================================================================

    /**
     * Select dict type list by filter entity (dictName / dictType / status).
     * Mirrors: SysDictTypeMapper.selectDictTypeList(SysDictType)
     */
    public Future<List<SysDictType>> selectDictTypeList(SysDictType filter) {
        if (filter == null) return findAll();
        return search(
            filter.getDictName(),
            filter.getDictType(),
            filter.getStatus()
        );
    }

    /**
     * Check dictType uniqueness — returns the entity if found (not unique),
     * null if not found (unique). Mirrors: SysDictTypeMapper.checkDictTypeUnique(String)
     */
    public Future<SysDictType> checkDictTypeUnique(String dictType) {
        return findByDictType(dictType);
    }

    // ================================================================
    // PAGINATION
    // ================================================================

    public Future<List<SysDictType>> findPaginated(int page, int size) {
        int offset = (page - 1) * size;
        String sql = "SELECT * FROM sys_dict_type ORDER BY dict_id LIMIT $1 OFFSET $2";
        Tuple params = Tuple.tuple().addInteger(size).addInteger(offset);
        return DatabaseVerticle.query(vertx, sql, params).map(this::toList);
    }

    public Future<List<SysDictType>> searchPaginated(String dictName, String dictType,
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

        return DatabaseVerticle.query(vertx, sql.toString(), params).map(this::toList);
    }

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
