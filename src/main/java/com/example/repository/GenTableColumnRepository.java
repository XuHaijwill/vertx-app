package com.example.repository;

import com.example.db.DatabaseVerticle;
import com.example.entity.GenTableColumn;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 代码生成业务字段 Repository
 * 对应 gen_table_column 表
 * 
 * 参考 RuoYi GenTableColumnMapper，提供相同的 SQL 功能：
 * - selectDbTableColumnsByName    → 根据表名称查询数据库列信息
 * - selectGenTableColumnListByTableId → 查询业务字段列表
 * - insertGenTableColumn          → 新增业务字段
 * - updateGenTableColumn          → 修改业务字段
 * - deleteGenTableColumnByIds     → 批量删除业务字段
 * - deleteGenTableColumns         → 根据字段列表删除
 */
public class GenTableColumnRepository {

    private static final Logger LOG = LoggerFactory.getLogger(GenTableColumnRepository.class);

    private final Vertx vertx;

    public GenTableColumnRepository(Vertx vertx) {
        this.vertx = vertx;
    }

    // ================================================================
    // Row → Entity converter
    // ================================================================

    private GenTableColumn toColumn(Row row) {
        return GenTableColumn.fromRow(row);
    }

    private List<GenTableColumn> toColumnList(RowSet<Row> rows) {
        List<GenTableColumn> list = new ArrayList<>();
        for (Row row : rows) {
            list.add(toColumn(row));
        }
        return list;
    }

    // ================================================================
    // QUERY OPERATIONS
    // ================================================================

    /**
     * 根据表名称查询数据库列信息（对应 RuoYi selectDbTableColumnsByName）
     * 
     * 从 PostgreSQL information_schema 查询列元数据：
     * - column_name: 列名
     * - column_type: 数据类型
     * - column_comment: 列注释（通过 col_description 获取）
     * - is_required: 是否必填（is_nullable = 'NO' 且非主键）
     * - is_pk: 是否主键（通过查询主键约束获取）
     * - is_increment: 是否自增（通过 column_default 检测 nextval）
     * - sort: 排序号
     *
     * @param tableName 表名称
     * @return 列信息集合
     */
    public Future<List<GenTableColumn>> selectDbTableColumnsByName(String tableName) {
        // PostgreSQL 版本：查询列信息 + 主键信息
        String sql = "SELECT c.column_name, c.data_type, c.character_maximum_length, " +
                "c.is_nullable, c.column_default, c.ordinal_position, " +
                "col_description((c.table_schema||'.'||c.table_name)::regclass, c.ordinal_position) as column_comment, " +
                "CASE WHEN pk.column_name IS NOT NULL THEN '1' ELSE '0' END as is_pk " +
                "FROM information_schema.columns c " +
                "LEFT JOIN ( " +
                "    SELECT kcu.column_name, kcu.table_name " +
                "    FROM information_schema.table_constraints tc " +
                "    JOIN information_schema.key_column_usage kcu " +
                "        ON tc.constraint_name = kcu.constraint_name " +
                "        AND tc.table_schema = kcu.table_schema " +
                "    WHERE tc.constraint_type = 'PRIMARY KEY' " +
                ") pk ON c.column_name = pk.column_name AND c.table_name = pk.table_name " +
                "WHERE c.table_schema = 'public' AND c.table_name = $1 " +
                "ORDER BY c.ordinal_position";

        return DatabaseVerticle.query(vertx, sql, Tuple.of(tableName))
                .map(rows -> {
                    List<GenTableColumn> list = new ArrayList<>();
                    int sort = 1;
                    for (Row row : rows) {
                        GenTableColumn col = new GenTableColumn();
                        col.setColumnName(row.getString("column_name"));
                        col.setColumnType(buildColumnType(row));
                        col.setColumnComment(row.getString("column_comment"));
                        col.setSort(sort++);
                        
                        // 是否主键
                        String isPk = row.getString("is_pk");
                        col.setIsPk(isPk != null ? isPk : "0");
                        
                        // 是否必填（is_nullable = 'NO' 且非主键）
                        String isNullable = row.getString("is_nullable");
                        if ("NO".equalsIgnoreCase(isNullable) && !"1".equals(isPk)) {
                            col.setIsRequired("1");
                        } else {
                            col.setIsRequired("0");
                        }
                        
                        // 是否自增（检测 nextval 序列）
                        String columnDefault = row.getString("column_default");
                        if (columnDefault != null && columnDefault.contains("nextval")) {
                            col.setIsIncrement("1");
                        } else {
                            col.setIsIncrement("0");
                        }
                        
                        list.add(col);
                    }
                    return list;
                });
    }

    /**
     * 构建完整的列类型字符串（含长度）
     */
    private String buildColumnType(Row row) {
        String dataType = row.getString("data_type");
        Long maxLength = row.getLong("character_maximum_length");
        
        if (maxLength != null && maxLength > 0) {
            return dataType + "(" + maxLength + ")";
        }
        return dataType;
    }

    /**
     * 查询业务字段列表（对应 RuoYi selectGenTableColumnListByTableId）
     *
     * @param tableId 业务字段编号（gen_table 的 table_id）
     * @return 业务字段集合
     */
    public Future<List<GenTableColumn>> selectGenTableColumnListByTableId(Long tableId) {
        String sql = "SELECT column_id, table_id, column_name, column_comment, column_type, " +
                "java_type, java_field, is_pk, is_increment, is_required, is_insert, is_edit, " +
                "is_list, is_query, query_type, html_type, dict_type, sort, create_by, create_time, " +
                "update_by, update_time " +
                "FROM gen_table_column WHERE table_id = $1 ORDER BY sort";
        
        return DatabaseVerticle.query(vertx, sql, Tuple.of(tableId))
                .map(this::toColumnList);
    }

    /**
     * 根据字段ID查询业务字段
     *
     * @param columnId 字段ID
     * @return 业务字段信息
     */
    public Future<GenTableColumn> selectGenTableColumnById(Long columnId) {
        String sql = "SELECT * FROM gen_table_column WHERE column_id = $1";
        
        return DatabaseVerticle.query(vertx, sql, Tuple.of(columnId))
                .map(rows -> {
                    if (rows.size() == 0) return null;
                    return GenTableColumn.fromRow(rows.iterator().next());
                });
    }

    // ================================================================
    // INSERT / UPDATE / DELETE OPERATIONS
    // ================================================================

    /**
     * 新增业务字段（对应 RuoYi insertGenTableColumn）
     *
     * @param column 业务字段信息
     * @return 新增后的字段ID
     */
    public Future<Long> insertGenTableColumn(GenTableColumn column) {
        String sql = "INSERT INTO gen_table_column (" +
                "table_id, column_name, column_comment, column_type, " +
                "java_type, java_field, is_pk, is_increment, is_required, " +
                "is_insert, is_edit, is_list, is_query, query_type, html_type, " +
                "dict_type, sort, create_by, create_time" +
                ") VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18, CURRENT_TIMESTAMP) " +
                "RETURNING column_id";

        return DatabaseVerticle.query(vertx, sql, Tuple.of(
                column.getTableId(),
                column.getColumnName(),
                column.getColumnComment(),
                column.getColumnType(),
                column.getJavaType() != null ? column.getJavaType() : "String",
                column.getJavaField(),
                column.getIsPk() != null ? column.getIsPk() : "0",
                column.getIsIncrement() != null ? column.getIsIncrement() : "0",
                column.getIsRequired() != null ? column.getIsRequired() : "0",
                column.getIsInsert() != null ? column.getIsInsert() : "1",
                column.getIsEdit() != null ? column.getIsEdit() : "1",
                column.getIsList() != null ? column.getIsList() : "1",
                column.getIsQuery() != null ? column.getIsQuery() : "0",
                column.getQueryType() != null ? column.getQueryType() : "EQ",
                column.getHtmlType() != null ? column.getHtmlType() : "input",
                column.getDictType(),
                column.getSort(),
                column.getCreateBy()
        )).map(rows -> rows.iterator().next().getLong("column_id"));
    }

    /**
     * 批量新增业务字段
     *
     * @param columns 业务字段列表
     * @return 新增数量
     */
    public Future<Integer> insertGenTableColumnBatch(List<GenTableColumn> columns) {
        if (columns == null || columns.isEmpty()) {
            return Future.succeededFuture(0);
        }

        // 逐条插入（PostgreSQL 批量 RETURNING 支持有限）
        List<Future<Long>> futures = new ArrayList<>();
        for (GenTableColumn column : columns) {
            futures.add(insertGenTableColumn(column));
        }

        return Future.all(futures)
                .map(composite -> futures.size());
    }

    /**
     * 修改业务字段（对应 RuoYi updateGenTableColumn）
     *
     * @param column 业务字段信息
     * @return 影响行数
     */
    public Future<Integer> updateGenTableColumn(GenTableColumn column) {
        String sql = "UPDATE gen_table_column SET " +
                "column_comment = $1, java_type = $2, java_field = $3, " +
                "is_pk = $4, is_increment = $5, is_required = $6, " +
                "is_insert = $7, is_edit = $8, is_list = $9, is_query = $10, " +
                "query_type = $11, html_type = $12, dict_type = $13, sort = $14, " +
                "update_by = $15, update_time = CURRENT_TIMESTAMP " +
                "WHERE column_id = $16";

        return DatabaseVerticle.query(vertx, sql, Tuple.of(
                column.getColumnComment(),
                column.getJavaType(),
                column.getJavaField(),
                column.getIsPk(),
                column.getIsIncrement(),
                column.getIsRequired(),
                column.getIsInsert(),
                column.getIsEdit(),
                column.getIsList(),
                column.getIsQuery(),
                column.getQueryType(),
                column.getHtmlType(),
                column.getDictType(),
                column.getSort(),
                column.getUpdateBy(),
                column.getColumnId()
        )).map(RowSet::rowCount);
    }

    /**
     * 批量删除业务字段（对应 RuoYi deleteGenTableColumnByIds）
     * 注意：RuoYi XML 中此方法实际是根据 table_id 删除（参数名是 tableId）
     * 这里实现根据 column_id 删除
     *
     * @param ids 需要删除的字段ID数组
     * @return 影响行数
     */
    public Future<Integer> deleteGenTableColumnByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Future.succeededFuture(0);
        }

        StringBuilder placeholders = new StringBuilder();
        for (int i = 1; i <= ids.size(); i++) {
            if (i > 1) placeholders.append(",");
            placeholders.append("$").append(i);
        }

        String sql = "DELETE FROM gen_table_column WHERE column_id IN (" + placeholders + ")";

        Tuple tuple = Tuple.tuple();
        ids.forEach(tuple::addValue);

        return DatabaseVerticle.query(vertx, sql, tuple)
                .map(RowSet::rowCount);
    }

    /**
     * 根据表ID删除业务字段（对应 RuoYi deleteGenTableColumnByIds 的实际行为）
     *
     * @param tableIds 表ID列表
     * @return 影响行数
     */
    public Future<Integer> deleteGenTableColumnByTableIds(List<Long> tableIds) {
        if (tableIds == null || tableIds.isEmpty()) {
            return Future.succeededFuture(0);
        }

        StringBuilder placeholders = new StringBuilder();
        for (int i = 1; i <= tableIds.size(); i++) {
            if (i > 1) placeholders.append(",");
            placeholders.append("$").append(i);
        }

        String sql = "DELETE FROM gen_table_column WHERE table_id IN (" + placeholders + ")";

        Tuple tuple = Tuple.tuple();
        tableIds.forEach(tuple::addValue);

        return DatabaseVerticle.query(vertx, sql, tuple)
                .map(RowSet::rowCount);
    }

    /**
     * 根据字段列表删除业务字段（对应 RuoYi deleteGenTableColumns）
     *
     * @param columns 字段列表（取 columnId）
     * @return 影响行数
     */
    public Future<Integer> deleteGenTableColumns(List<GenTableColumn> columns) {
        if (columns == null || columns.isEmpty()) {
            return Future.succeededFuture(0);
        }

        List<Long> ids = new ArrayList<>();
        for (GenTableColumn col : columns) {
            if (col.getColumnId() != null) {
                ids.add(col.getColumnId());
            }
        }

        return deleteGenTableColumnByIds(ids);
    }

    /**
     * 根据表ID删除所有字段
     *
     * @param tableId 表ID
     * @return 影响行数
     */
    public Future<Integer> deleteGenTableColumnByTableId(Long tableId) {
        String sql = "DELETE FROM gen_table_column WHERE table_id = $1";
        return DatabaseVerticle.query(vertx, sql, Tuple.of(tableId))
                .map(RowSet::rowCount);
    }
}
