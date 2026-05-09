package com.example.repository;

import com.example.db.DatabaseVerticle;
import com.example.entity.GenTable;
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
 * 代码生成业务表 Repository
 * 对应 gen_table 表
 */
public class GenTableRepository {

    private static final Logger LOG = LoggerFactory.getLogger(GenTableRepository.class);

    private final Vertx vertx;

    public GenTableRepository(Vertx vertx) {
        this.vertx = vertx;
    }

    // ================================================================
    // GenTable 查询操作
    // ================================================================

    /**
     * 查询业务表列表
     */
    public Future<List<GenTable>> selectGenTableList(String tableName, String tableComment) {
        StringBuilder sql = new StringBuilder("SELECT * FROM gen_table WHERE 1=1");
        List<Object> params = new ArrayList<>();
        int idx = 1;

        if (tableName != null && !tableName.isEmpty()) {
            sql.append(" AND table_name LIKE $" + idx++);
            params.add("%" + tableName + "%");
        }
        if (tableComment != null && !tableComment.isEmpty()) {
            sql.append(" AND table_comment LIKE $" + idx++);
            params.add("%" + tableComment + "%");
        }
        sql.append(" ORDER BY table_id DESC");

        Tuple tuple = Tuple.tuple();
        params.forEach(tuple::addValue);

        return DatabaseVerticle.query(vertx, sql.toString(), tuple)
                .map(rows -> {
                    List<GenTable> list = new ArrayList<>();
                    for (Row row : rows) {
                        list.add(GenTable.fromRow(row));
                    }
                    return list;
                });
    }

    /**
     * 查询数据库表列表（从 information_schema）
     */
    public Future<List<GenTable>> selectDbTableList(String tableName, String tableComment) {
        StringBuilder sql = new StringBuilder(
                "SELECT table_name, obj_description((table_schema||'.'||table_name)::regclass) as table_comment " +
                "FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' " +
                "AND table_name NOT LIKE 'pg_%' AND table_name NOT LIKE 'sql_%' " +
                "AND table_name NOT IN (SELECT table_name FROM gen_table)");
        List<Object> params = new ArrayList<>();
        int idx = 1;

        if (tableName != null && !tableName.isEmpty()) {
            sql.append(" AND table_name LIKE $" + idx++);
            params.add("%" + tableName + "%");
        }

        sql.append(" ORDER BY table_name");

        Tuple tuple = Tuple.tuple();
        params.forEach(tuple::addValue);

        return DatabaseVerticle.query(vertx, sql.toString(), tuple)
                .map(rows -> {
                    List<GenTable> list = new ArrayList<>();
                    for (Row row : rows) {
                        GenTable t = new GenTable();
                        t.setTableName(row.getString("table_name"));
                        t.setTableComment(row.getString("table_comment"));
                        list.add(t);
                    }
                    return list;
                });
    }

    /**
     * 根据表名称数组查询数据库表列表
     */
    public Future<List<GenTable>> selectDbTableListByNames(List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return Future.succeededFuture(new ArrayList<>());
        }

        StringBuilder placeholders = new StringBuilder();
        for (int i = 1; i <= tableNames.size(); i++) {
            if (i > 1) placeholders.append(",");
            placeholders.append("$").append(i);
        }

        String sql = "SELECT table_name, obj_description((table_schema||'.'||table_name)::regclass) as table_comment " +
                "FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' " +
                "AND table_name IN (" + placeholders + ")";

        Tuple tuple = Tuple.tuple();
        tableNames.forEach(tuple::addValue);

        return DatabaseVerticle.query(vertx, sql, tuple)
                .map(rows -> {
                    List<GenTable> list = new ArrayList<>();
                    for (Row row : rows) {
                        GenTable t = new GenTable();
                        t.setTableName(row.getString("table_name"));
                        t.setTableComment(row.getString("table_comment"));
                        list.add(t);
                    }
                    return list;
                });
    }

    /**
     * 查询所有业务表信息
     */
    public Future<List<GenTable>> selectGenTableAll() {
        String sql = "SELECT * FROM gen_table ORDER BY table_id DESC";
        return DatabaseVerticle.query(vertx, sql)
                .map(rows -> {
                    List<GenTable> list = new ArrayList<>();
                    for (Row row : rows) {
                        list.add(GenTable.fromRow(row));
                    }
                    return list;
                });
    }

    /**
     * 根据表ID查询业务表信息（含列信息）
     */
    public Future<GenTable> selectGenTableById(Long tableId) {
        String sql = "SELECT * FROM gen_table WHERE table_id = $1";
        return DatabaseVerticle.query(vertx, sql, Tuple.of(tableId))
                .map(rows -> {
                    if (rows.size() == 0) return null;
                    GenTable table = GenTable.fromRow(rows.iterator().next());
                    return table;
                })
                .compose(table -> {
                    if (table == null) return Future.succeededFuture(null);
                    // 查询关联的列信息
                    return selectGenTableColumnListByTableId(tableId)
                            .map(columns -> {
                                table.setColumns(columns);
                                // 设置主键列
                                for (GenTableColumn col : columns) {
                                    if (col.isPk()) {
                                        table.setPkColumn(col);
                                        break;
                                    }
                                }
                                return table;
                            });
                });
    }

    /**
     * 根据表名称查询业务表信息
     */
    public Future<GenTable> selectGenTableByName(String tableName) {
        String sql = "SELECT * FROM gen_table WHERE table_name = $1";
        return DatabaseVerticle.query(vertx, sql, Tuple.of(tableName))
                .map(rows -> {
                    if (rows.size() == 0) return null;
                    return GenTable.fromRow(rows.iterator().next());
                });
    }

    /**
     * 新增业务表
     */
    public Future<Long> insertGenTable(GenTable table) {
        String sql = "INSERT INTO gen_table (table_name, table_comment, sub_table_name, sub_table_fk_name, " +
                "class_name, tpl_category, tpl_web_type, package_name, module_name, business_name, " +
                "function_name, function_author, form_col_num, gen_type, gen_path, options, " +
                "tree_code, tree_parent_code, tree_name, parent_menu_id, create_by, create_time, remark) " +
                "VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18, $19, $20, $21, CURRENT_TIMESTAMP, $22) " +
                "RETURNING table_id";

        return DatabaseVerticle.query(vertx, sql, Tuple.of(
                table.getTableName(),
                table.getTableComment(),
                table.getSubTableName(),
                table.getSubTableFkName(),
                table.getClassName(),
                table.getTplCategory() != null ? table.getTplCategory() : "crud",
                table.getTplWebType(),
                table.getPackageName(),
                table.getModuleName(),
                table.getBusinessName(),
                table.getFunctionName(),
                table.getFunctionAuthor(),
                table.getFormColNum() != null ? table.getFormColNum() : 1,
                table.getGenType() != null ? table.getGenType() : "0",
                table.getGenPath() != null ? table.getGenPath() : "/",
                table.getOptions(),
                table.getTreeCode(),
                table.getTreeParentCode(),
                table.getTreeName(),
                table.getParentMenuId(),
                table.getCreateBy(),
                table.getRemark()
        )).map(rows -> rows.iterator().next().getLong("table_id"));
    }

    /**
     * 修改业务表
     */
    public Future<Integer> updateGenTable(GenTable table) {
        String sql = "UPDATE gen_table SET table_name = $1, table_comment = $2, sub_table_name = $3, " +
                "sub_table_fk_name = $4, class_name = $5, tpl_category = $6, tpl_web_type = $7, " +
                "package_name = $8, module_name = $9, business_name = $10, function_name = $11, " +
                "function_author = $12, form_col_num = $13, gen_type = $14, gen_path = $15, options = $16, " +
                "tree_code = $17, tree_parent_code = $18, tree_name = $19, parent_menu_id = $20, " +
                "update_by = $21, update_time = CURRENT_TIMESTAMP, remark = $22 WHERE table_id = $23";

        return DatabaseVerticle.query(vertx, sql, Tuple.of(
                table.getTableName(),
                table.getTableComment(),
                table.getSubTableName(),
                table.getSubTableFkName(),
                table.getClassName(),
                table.getTplCategory(),
                table.getTplWebType(),
                table.getPackageName(),
                table.getModuleName(),
                table.getBusinessName(),
                table.getFunctionName(),
                table.getFunctionAuthor(),
                table.getFormColNum(),
                table.getGenType(),
                table.getGenPath(),
                table.getOptions(),
                table.getTreeCode(),
                table.getTreeParentCode(),
                table.getTreeName(),
                table.getParentMenuId(),
                table.getUpdateBy(),
                table.getRemark(),
                table.getTableId()
        )).map(RowSet::rowCount);
    }

    /**
     * 批量删除业务表
     */
    public Future<Integer> deleteGenTableByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Future.succeededFuture(0);
        }

        StringBuilder placeholders = new StringBuilder();
        for (int i = 1; i <= ids.size(); i++) {
            if (i > 1) placeholders.append(",");
            placeholders.append("$").append(i);
        }

        String sql = "DELETE FROM gen_table WHERE table_id IN (" + placeholders + ")";

        Tuple tuple = Tuple.tuple();
        ids.forEach(tuple::addValue);

        return DatabaseVerticle.query(vertx, sql, tuple)
                .map(RowSet::rowCount);
    }

    /**
     * 创建表（执行DDL）
     */
    public Future<Integer> createTable(String sql) {
        return DatabaseVerticle.query(vertx, sql)
                .map(RowSet::rowCount);
    }

    // ================================================================
    // GenTableColumn 查询操作
    // ================================================================

    /**
     * 根据表名称查询数据库列信息
     */
    public Future<List<GenTableColumn>> selectDbTableColumnsByName(String tableName) {
        String sql = "SELECT column_name, data_type, character_maximum_length, " +
                "col_description((table_schema||'.'||table_name)::regclass, ordinal_position) as column_comment, " +
                "is_nullable, column_default " +
                "FROM information_schema.columns WHERE table_schema = 'public' AND table_name = $1 " +
                "ORDER BY ordinal_position";

        return DatabaseVerticle.query(vertx, sql, Tuple.of(tableName))
                .map(rows -> {
                    List<GenTableColumn> list = new ArrayList<>();
                    int sort = 1;
                    for (Row row : rows) {
                        GenTableColumn col = new GenTableColumn();
                        col.setColumnName(row.getString("column_name"));
                        col.setColumnType(row.getString("data_type"));
                        col.setColumnComment(row.getString("column_comment"));
                        col.setSort(sort++);
                        list.add(col);
                    }
                    return list;
                });
    }

    /**
     * 根据表ID查询列信息列表
     */
    public Future<List<GenTableColumn>> selectGenTableColumnListByTableId(Long tableId) {
        String sql = "SELECT * FROM gen_table_column WHERE table_id = $1 ORDER BY sort";
        return DatabaseVerticle.query(vertx, sql, Tuple.of(tableId))
                .map(rows -> {
                    List<GenTableColumn> list = new ArrayList<>();
                    for (Row row : rows) {
                        list.add(GenTableColumn.fromRow(row));
                    }
                    return list;
                });
    }

    /**
     * 新增业务字段
     */
    public Future<Long> insertGenTableColumn(GenTableColumn column) {
        String sql = "INSERT INTO gen_table_column (table_id, column_name, column_comment, column_type, " +
                "java_type, java_field, is_pk, is_increment, is_required, is_insert, is_edit, is_list, " +
                "is_query, query_type, html_type, dict_type, sort, create_by, create_time) " +
                "VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18, CURRENT_TIMESTAMP) " +
                "RETURNING column_id";

        return DatabaseVerticle.query(vertx, sql, Tuple.of(
                column.getTableId(),
                column.getColumnName(),
                column.getColumnComment(),
                column.getColumnType(),
                column.getJavaType(),
                column.getJavaField(),
                column.getIsPk(),
                column.getIsIncrement(),
                column.getIsRequired(),
                column.getIsInsert(),
                column.getIsEdit(),
                column.getIsList(),
                column.getIsQuery(),
                column.getQueryType() != null ? column.getQueryType() : "EQ",
                column.getHtmlType(),
                column.getDictType(),
                column.getSort(),
                column.getCreateBy()
        )).map(rows -> rows.iterator().next().getLong("column_id"));
    }

    /**
     * 修改业务字段
     */
    public Future<Integer> updateGenTableColumn(GenTableColumn column) {
        String sql = "UPDATE gen_table_column SET column_name = $1, column_comment = $2, column_type = $3, " +
                "java_type = $4, java_field = $5, is_pk = $6, is_increment = $7, is_required = $8, " +
                "is_insert = $9, is_edit = $10, is_list = $11, is_query = $12, query_type = $13, " +
                "html_type = $14, dict_type = $15, sort = $16, update_by = $17, update_time = CURRENT_TIMESTAMP " +
                "WHERE column_id = $18";

        return DatabaseVerticle.query(vertx, sql, Tuple.of(
                column.getColumnName(),
                column.getColumnComment(),
                column.getColumnType(),
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
     * 批量删除业务字段
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
     * 根据表ID删除业务字段
     */
    public Future<Integer> deleteGenTableColumnByTableId(Long tableId) {
        String sql = "DELETE FROM gen_table_column WHERE table_id = $1";
        return DatabaseVerticle.query(vertx, sql, Tuple.of(tableId))
                .map(RowSet::rowCount);
    }

    // ================================================================
    // 分页查询
    // ================================================================

    /**
     * 分页查询业务表列表
     */
    public Future<List<GenTable>> selectGenTableListPaginated(String tableName, String tableComment, int page, int size) {
        StringBuilder sql = new StringBuilder("SELECT * FROM gen_table WHERE 1=1");
        List<Object> params = new ArrayList<>();
        int idx = 1;

        if (tableName != null && !tableName.isEmpty()) {
            sql.append(" AND table_name LIKE $" + idx++);
            params.add("%" + tableName + "%");
        }
        if (tableComment != null && !tableComment.isEmpty()) {
            sql.append(" AND table_comment LIKE $" + idx++);
            params.add("%" + tableComment + "%");
        }
        sql.append(" ORDER BY table_id DESC LIMIT $" + idx++ + " OFFSET $" + idx++);
        params.add(size);
        params.add((page - 1) * size);

        Tuple tuple = Tuple.tuple();
        params.forEach(tuple::addValue);

        return DatabaseVerticle.query(vertx, sql.toString(), tuple)
                .map(rows -> {
                    List<GenTable> list = new ArrayList<>();
                    for (Row row : rows) {
                        list.add(GenTable.fromRow(row));
                    }
                    return list;
                });
    }

    /**
     * 查询业务表总数
     */
    public Future<Long> selectGenTableListCount(String tableName, String tableComment) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) as total FROM gen_table WHERE 1=1");
        List<Object> params = new ArrayList<>();
        int idx = 1;

        if (tableName != null && !tableName.isEmpty()) {
            sql.append(" AND table_name LIKE $" + idx++);
            params.add("%" + tableName + "%");
        }
        if (tableComment != null && !tableComment.isEmpty()) {
            sql.append(" AND table_comment LIKE $" + idx++);
            params.add("%" + tableComment + "%");
        }

        Tuple tuple = Tuple.tuple();
        params.forEach(tuple::addValue);

        return DatabaseVerticle.query(vertx, sql.toString(), tuple)
                .map(rows -> rows.iterator().next().getLong("total"));
    }
}
