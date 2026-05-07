package com.example.repository;

import com.example.db.DatabaseVerticle;
import com.example.entity.SysDictData;
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
 * SysDictData Repository - Database operations for sys_dict_data table
 */
public class SysDictDataRepository {

    private final Vertx vertx;

    public SysDictDataRepository(Vertx vertx) {
        this.vertx = vertx;
    }

    // ================================================================
    // ROW → ENTITY HELPERS
    // ================================================================

    private List<SysDictData> toList(RowSet<Row> rows) {
        List<SysDictData> list = new ArrayList<>();
        for (Row row : rows) {
            list.add(SysDictData.toSysDictData(row));
        }
        return list;
    }

    private SysDictData toOne(RowSet<Row> rows) {
        return rows.iterator().hasNext() ? SysDictData.toSysDictData(rows.iterator().next()) : null;
    }

    // ================================================================
    // QUERY OPERATIONS
    // ================================================================

    public Future<List<SysDictData>> findAll() {
        String sql = "SELECT * FROM sys_dict_data ORDER BY dict_sort, dict_code";
        return DatabaseVerticle.query(vertx, sql).map(this::toList);
    }

    public Future<SysDictData> findById(Long dictCode) {
        String sql = "SELECT * FROM sys_dict_data WHERE dict_code = $1";
        Tuple params = Tuple.tuple().addLong(dictCode);
        return DatabaseVerticle.query(vertx, sql, params).map(this::toOne);
    }

    public Future<List<SysDictData>> findByDictType(String dictType) {
        String sql = "SELECT * FROM sys_dict_data WHERE dict_type = $1 ORDER BY dict_sort, dict_code";
        Tuple params = Tuple.tuple().addString(dictType);
        return DatabaseVerticle.query(vertx, sql, params).map(this::toList);
    }

    public Future<SysDictData> findByDictTypeAndValue(String dictType, String dictValue) {
        String sql = "SELECT * FROM sys_dict_data WHERE dict_type = $1 AND dict_value = $2";
        Tuple params = Tuple.tuple().addString(dictType).addString(dictValue);
        return DatabaseVerticle.query(vertx, sql, params).map(this::toOne);
    }

    public Future<List<SysDictData>> findByDictLabel(String dictLabel) {
        if (dictLabel == null || dictLabel.isEmpty()) {
            return findAll();
        }
        String sql = "SELECT * FROM sys_dict_data WHERE dict_label LIKE $1 ORDER BY dict_sort, dict_code";
        Tuple params = Tuple.tuple().addString("%" + dictLabel + "%");
        return DatabaseVerticle.query(vertx, sql, params).map(this::toList);
    }

    public Future<List<SysDictData>> findByStatus(String status) {
        String sql = "SELECT * FROM sys_dict_data WHERE status = $1 ORDER BY dict_sort, dict_code";
        Tuple params = Tuple.tuple().addString(status);
        return DatabaseVerticle.query(vertx, sql, params).map(this::toList);
    }

    public Future<List<SysDictData>> findDefaultsByDictType(String dictType) {
        String sql = "SELECT * FROM sys_dict_data WHERE dict_type = $1 AND is_default = 'Y' ORDER BY dict_sort, dict_code";
        Tuple params = Tuple.tuple().addString(dictType);
        return DatabaseVerticle.query(vertx, sql, params).map(this::toList);
    }

    public Future<List<SysDictData>> search(String dictType, String dictLabel,
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

        return DatabaseVerticle.query(vertx, sql.toString(), params).map(this::toList);
    }

    public Future<Long> count() {
        String sql = "SELECT COUNT(*) as count FROM sys_dict_data";
        return DatabaseVerticle.query(vertx, sql)
            .map(rows -> rows.iterator().next().getLong("count"));
    }

    public Future<Long> countByDictType(String dictType) {
        String sql = "SELECT COUNT(*) as count FROM sys_dict_data WHERE dict_type = $1";
        Tuple params = Tuple.tuple().addString(dictType);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getLong("count"));
    }

    public Future<Boolean> existsByDictType(String dictType) {
        String sql = "SELECT COUNT(*) as count FROM sys_dict_data WHERE dict_type = $1";
        Tuple params = Tuple.tuple().addString(dictType);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getLong("count") > 0);
    }

    public Future<Boolean> existsByDictTypeAndValue(String dictType, String dictValue) {
        String sql = "SELECT COUNT(*) as count FROM sys_dict_data WHERE dict_type = $1 AND dict_value = $2";
        Tuple params = Tuple.tuple().addString(dictType).addString(dictValue);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getLong("count") > 0);
    }

    // ================================================================
    // MUTATION OPERATIONS
    // ================================================================

    public Future<SysDictData> create(SysDictData dict) {
        String sql = """
            INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type,
                                       css_class, list_class, is_default, status, create_by, remark)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
            RETURNING *
            """;
        Tuple params = Tuple.tuple()
            .addInteger(dict.getDictSort() != null ? dict.getDictSort() : 0)
            .addString(dict.getDictLabel())
            .addString(dict.getDictValue())
            .addString(dict.getDictType())
            .addString(dict.getCssClass())
            .addString(dict.getListClass())
            .addString(dict.getIsDefault() != null ? dict.getIsDefault() : "N")
            .addString(dict.getStatus() != null ? dict.getStatus() : "0")
            .addString(dict.getCreateBy() != null ? dict.getCreateBy() : "admin")
            .addString(dict.getRemark());
        return DatabaseVerticle.query(vertx, sql, params).map(this::toOne);
    }

    public Future<SysDictData> update(Long dictCode, SysDictData dict) {
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
            .addInteger(dict.getDictSort())
            .addString(dict.getDictLabel())
            .addString(dict.getDictValue())
            .addString(dict.getDictType())
            .addString(dict.getCssClass())
            .addString(dict.getListClass())
            .addString(dict.getIsDefault())
            .addString(dict.getStatus())
            .addString(dict.getUpdateBy())
            .addString(dict.getRemark());
        return DatabaseVerticle.query(vertx, sql, params).map(this::toOne);
    }

    public Future<Boolean> delete(Long dictCode) {
        String sql = "DELETE FROM sys_dict_data WHERE dict_code = $1 RETURNING dict_code";
        Tuple params = Tuple.tuple().addLong(dictCode);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.rowCount() > 0);
    }

    public Future<Integer> deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Future.succeededFuture(0);
        String placeholders = ids.stream().map(i -> "$" + (ids.indexOf(i) + 1)).collect(Collectors.joining(","));
        String sql = "DELETE FROM sys_dict_data WHERE dict_code IN (" + placeholders + ")";
        Tuple params = Tuple.tuple();
        for (Long id : ids) params.addLong(id);
        return DatabaseVerticle.query(vertx, sql, params).map(rows -> rows.rowCount());
    }

    public Future<Integer> deleteByDictType(String dictType) {
        String sql = "DELETE FROM sys_dict_data WHERE dict_type = $1";
        Tuple params = Tuple.tuple().addString(dictType);
        return DatabaseVerticle.query(vertx, sql, params).map(rows -> rows.rowCount());
    }

    // ================================================================
    // PAGINATION
    // ================================================================

    public Future<List<SysDictData>> findPaginated(int page, int size) {
        int offset = (page - 1) * size;
        String sql = "SELECT * FROM sys_dict_data ORDER BY dict_sort, dict_code LIMIT $1 OFFSET $2";
        Tuple params = Tuple.tuple().addInteger(size).addInteger(offset);
        return DatabaseVerticle.query(vertx, sql, params).map(this::toList);
    }

    public Future<List<SysDictData>> searchPaginated(String dictType, String dictLabel,
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

        return DatabaseVerticle.query(vertx, sql.toString(), params).map(this::toList);
    }

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

    public Future<List<SysDictData>> createBatch(List<SysDictData> items) {
        if (items == null || items.isEmpty()) return Future.succeededFuture(List.of());

        List<String> columns = List.of("dict_sort", "dict_label", "dict_value", "dict_type",
                                       "css_class", "list_class", "is_default", "status", "create_by", "remark");
        List<List<Object>> values = items.stream().map(d -> List.<Object>of(
            d.getDictSort() != null ? d.getDictSort() : 0,
            d.getDictLabel(),
            d.getDictValue(),
            d.getDictType(),
            d.getCssClass(),
            d.getListClass(),
            d.getIsDefault() != null ? d.getIsDefault() : "N",
            d.getStatus() != null ? d.getStatus() : "0",
            d.getCreateBy() != null ? d.getCreateBy() : "admin",
            d.getRemark()
        )).collect(Collectors.toList());

        return com.example.db.BatchOperations.multiRowInsert(vertx, "sys_dict_data", columns, values, "dict_code")
            .compose(ids -> {
                if (ids.isEmpty()) return Future.succeededFuture(List.<SysDictData>of());
                String placeholders = ids.stream().map(i -> "$" + (ids.indexOf(i) + 1)).collect(Collectors.joining(","));
                Tuple params = Tuple.tuple();
                for (Long id : ids) params.addLong(id);
                return DatabaseVerticle.query(vertx, "SELECT * FROM sys_dict_data WHERE dict_code IN (" + placeholders + ") ORDER BY dict_code", params)
                    .map(this::toList);
            });
    }
}
