package com.example.api;

import com.example.core.ApiResponse;
import com.example.core.PageResult;
import com.example.entity.GenTable;
import com.example.entity.GenTableColumn;
import com.example.repository.GenTableRepository;
import com.example.repository.GenTableColumnRepository;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 代码生成 API - REST endpoints for code generation
 *
 * 对应 RuoYi GenController，提供相同的 HTTP 接口：
 * - GET    /api/gen/tables                 - 查询代码生成列表（分页）
 * - GET    /api/gen/tables/:id             - 获取代码生成详情（含字段列表）
 * - GET    /api/gen/db/tables              - 查询数据库表列表
 * - GET    /api/gen/db/columns/:tableName  - 查询数据表字段列表
 * - POST   /api/gen/import                 - 导入表结构
 * - POST   /api/gen/create-table           - 创建表结构
 * - PUT    /api/gen/tables/:id             - 修改代码生成配置
 * - DELETE /api/gen/tables/:ids            - 删除代码生成配置
 * - GET    /api/gen/preview/:id            - 预览代码
 * - GET    /api/gen/download/:tableName    - 生成代码下载
 * - GET    /api/gen/sync/:tableName        - 同步数据库
 */
public class GenTableApi extends BaseApi {

    private final GenTableRepository tableRepo;
    private final GenTableColumnRepository columnRepo;

    public GenTableApi(Vertx vertx) {
        super(vertx);
        this.tableRepo = new GenTableRepository(vertx);
        this.columnRepo = new GenTableColumnRepository(vertx);
    }

    @Override
    public void registerRoutes(Router router, String contextPath) {
        // 表管理
        router.get(contextPath + "/api/gen/tables").handler(this::listTables);
        router.get(contextPath + "/api/gen/tables/:id").handler(this::getTable);
        router.put(contextPath + "/api/gen/tables/:id").handler(this::updateTable);
        router.delete(contextPath + "/api/gen/tables/:id").handler(this::deleteTable);
        router.delete(contextPath + "/api/gen/tables").handler(this::batchDeleteTables);

        // 数据库表查询
        router.get(contextPath + "/api/gen/db/tables").handler(this::listDbTables);
        router.get(contextPath + "/api/gen/db/columns/:tableName").handler(this::listDbColumns);

        // 导入与创建
        router.post(contextPath + "/api/gen/import").handler(this::importTable);
        router.post(contextPath + "/api/gen/create-table").handler(this::createTable);

        // 代码生成
        router.get(contextPath + "/api/gen/preview/:id").handler(this::previewCode);
        router.get(contextPath + "/api/gen/download/:tableName").handler(this::downloadCode);
        router.get(contextPath + "/api/gen/sync/:tableName").handler(this::syncDb);
    }

    // ================================================================
    // TABLE MANAGEMENT
    // ================================================================

    /**
     * 查询代码生成列表（分页）
     * GET /api/gen/tables?tableName=xxx&tableComment=xxx&page=1&size=20
     */
    private void listTables(RoutingContext ctx) {
        String tableName = queryStr(ctx, "tableName");
        String tableComment = queryStr(ctx, "tableComment");
        int page = queryInt(ctx, "page", 1);
        int size = queryIntClamped(ctx, "size", 20, 1, 100);

        tableRepo.selectGenTableListCount(tableName, tableComment)
            .flatMap(total -> tableRepo.selectGenTableListPaginated(tableName, tableComment, page, size)
                .map(list -> new PageResult<>(list, total, page, size)))
            .onSuccess(pageResult -> {
                ctx.json(ApiResponse.success(pageResult.toJson()).toJson());
            })
            .onFailure(err -> fail(ctx, err));
    }

    /**
     * 获取代码生成详情（含字段列表）
     * GET /api/gen/tables/:id
     */
    private void getTable(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) { badRequest(ctx, "Invalid table ID"); return; }

        tableRepo.selectGenTableById(id)
            .flatMap(table -> {
                if (table == null) {
                    return Future.succeededFuture(null);
                }
                // 查询关联的列信息
                return columnRepo.selectGenTableColumnListByTableId(id)
                    .map(columns -> {
                        table.setColumns(columns);
                        return table;
                    });
            })
            .onSuccess(table -> {
                if (table == null) {
                    notFound(ctx, "Table not found");
                } else {
                    // 构建响应数据
                    Map<String, Object> result = new HashMap<>();
                    result.put("info", table);
                    result.put("rows", table.getColumns());
                    ok(ctx, result);
                }
            })
            .onFailure(err -> fail(ctx, err));
    }

    /**
     * 修改代码生成配置
     * PUT /api/gen/tables/:id
     * Body: GenTable JSON
     */
    private void updateTable(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) { badRequest(ctx, "Invalid table ID"); return; }

        JsonObject body = ctx.body().asJsonObject();
        if (body == null) { badRequest(ctx, "Request body is required"); return; }

        GenTable table = GenTable.fromJson(body);
        table.setTableId(id);

        tableRepo.updateGenTable(table)
            .onSuccess(rows -> {
                if (rows > 0) {
                    ok(ctx, Map.of("updated", rows));
                } else {
                    notFound(ctx, "Table not found");
                }
            })
            .onFailure(err -> fail(ctx, err));
    }

    /**
     * 删除代码生成配置（单条）
     * DELETE /api/gen/tables/:id
     */
    private void deleteTable(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) { badRequest(ctx, "Invalid table ID"); return; }

        // 先删除关联的列配置
        columnRepo.deleteGenTableColumnByTableId(id)
            .flatMap(v -> tableRepo.deleteGenTableByIds(List.of(id)))
            .onSuccess(rows -> noContent(ctx))
            .onFailure(err -> fail(ctx, err));
    }

    /**
     * 批量删除代码生成配置
     * DELETE /api/gen/tables?ids=1,2,3
     */
    private void batchDeleteTables(RoutingContext ctx) {
        String idsStr = queryStr(ctx, "ids");
        if (idsStr == null || idsStr.isBlank()) {
            badRequest(ctx, "ids parameter is required");
            return;
        }

        List<Long> ids = parseIds(idsStr);
        if (ids.isEmpty()) {
            badRequest(ctx, "Invalid ids format");
            return;
        }

        // 先删除关联的列配置
        columnRepo.deleteGenTableColumnByTableIds(ids)
            .flatMap(v -> tableRepo.deleteGenTableByIds(ids))
            .onSuccess(rows -> noContent(ctx))
            .onFailure(err -> fail(ctx, err));
    }

    // ================================================================
    // DATABASE TABLE QUERY
    // ================================================================

    /**
     * 查询数据库表列表
     * GET /api/gen/db/tables?tableName=xxx&tableComment=xxx
     */
    private void listDbTables(RoutingContext ctx) {
        String tableName = queryStr(ctx, "tableName");
        String tableComment = queryStr(ctx, "tableComment");

        tableRepo.selectDbTableList(tableName, tableComment)
            .onSuccess(tables -> ok(ctx, tables))
            .onFailure(err -> fail(ctx, err));
    }

    /**
     * 查询数据表字段列表
     * GET /api/gen/db/columns/:tableName
     */
    private void listDbColumns(RoutingContext ctx) {
        String tableName = ctx.pathParam("tableName");
        if (tableName == null || tableName.isBlank()) {
            badRequest(ctx, "tableName is required");
            return;
        }

        columnRepo.selectDbTableColumnsByName(tableName)
            .onSuccess(columns -> ok(ctx, columns))
            .onFailure(err -> fail(ctx, err));
    }

    // ================================================================
    // IMPORT & CREATE
    // ================================================================

    /**
     * 导入表结构
     * POST /api/gen/import
     * Body: { "tables": "table1,table2", "tplWebType": "element-plus" }
     */
    private void importTable(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) { badRequest(ctx, "Request body is required"); return; }

        String tablesStr = body.getString("tables");
        String tplWebType = body.getString("tplWebType", "element-plus");

        if (tablesStr == null || tablesStr.isBlank()) {
            badRequest(ctx, "tables is required");
            return;
        }

        String[] tableNames = tablesStr.split(",");

        // 查询表信息并导入
        tableRepo.selectDbTableListByNames(List.of(tableNames))
            .flatMap(tableList -> {
                // 设置默认值
                for (GenTable table : tableList) {
                    table.setTplWebType(tplWebType);
                    // 设置默认包名等配置
                    if (table.getPackageName() == null) {
                        table.setPackageName("com.example");
                    }
                }
                // 批量插入表和列
                return importTablesWithColumns(tableList);
            })
            .onSuccess(v -> ok(ctx, Map.of("imported", tableNames.length)))
            .onFailure(err -> fail(ctx, err));
    }

    /**
     * 创建表结构（执行DDL）
     * POST /api/gen/create-table
     * Body: { "sql": "CREATE TABLE ...", "tplWebType": "element-plus" }
     */
    private void createTable(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) { badRequest(ctx, "Request body is required"); return; }

        String sql = body.getString("sql");
        String tplWebType = body.getString("tplWebType", "element-plus");

        if (sql == null || sql.isBlank()) {
            badRequest(ctx, "sql is required");
            return;
        }

        // 基本安全校验：只允许 CREATE TABLE
        String sqlUpper = sql.trim().toUpperCase();
        if (!sqlUpper.startsWith("CREATE TABLE")) {
            badRequest(ctx, "Only CREATE TABLE statements are allowed");
            return;
        }

        // 执行DDL
        tableRepo.createTable(sql)
            .onSuccess(v -> ok(ctx, Map.of("created", true)))
            .onFailure(err -> fail(ctx, err));
    }

    // ================================================================
    // CODE GENERATION
    // ================================================================

    /**
     * 预览代码
     * GET /api/gen/preview/:id
     * 
     * 返回模板预览数据（实际代码生成需要模板引擎支持）
     */
    private void previewCode(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) { badRequest(ctx, "Invalid table ID"); return; }

        tableRepo.selectGenTableById(id)
            .flatMap(table -> {
                if (table == null) {
                    return Future.succeededFuture(null);
                }
                return columnRepo.selectGenTableColumnListByTableId(id)
                    .map(columns -> {
                        table.setColumns(columns);
                        return table;
                    });
            })
            .onSuccess(table -> {
                if (table == null) {
                    notFound(ctx, "Table not found");
                } else {
                    // 返回预览数据（实际需要模板引擎）
                    Map<String, String> preview = generatePreview(table);
                    ok(ctx, preview);
                }
            })
            .onFailure(err -> fail(ctx, err));
    }

    /**
     * 生成代码下载
     * GET /api/gen/download/:tableName
     */
    private void downloadCode(RoutingContext ctx) {
        String tableName = ctx.pathParam("tableName");
        if (tableName == null || tableName.isBlank()) {
            badRequest(ctx, "tableName is required");
            return;
        }

        // TODO: 实际代码生成需要模板引擎
        ctx.response()
            .putHeader("Content-Type", "application/octet-stream")
            .putHeader("Content-Disposition", "attachment; filename=\"" + tableName + ".zip\"")
            .end("Code generation not yet implemented");
    }

    /**
     * 同步数据库
     * GET /api/gen/sync/:tableName
     */
    private void syncDb(RoutingContext ctx) {
        String tableName = ctx.pathParam("tableName");
        if (tableName == null || tableName.isBlank()) {
            badRequest(ctx, "tableName is required");
            return;
        }

        // 查询表配置
        tableRepo.selectGenTableByName(tableName)
            .flatMap(table -> {
                if (table == null) {
                    return Future.succeededFuture(null);
                }
                // 重新查询数据库列信息
                return columnRepo.selectDbTableColumnsByName(tableName)
                    .map(columns -> Map.of("table", table, "columns", columns));
            })
            .onSuccess(result -> {
                if (result == null) {
                    notFound(ctx, "Table not found in gen_table");
                } else {
                    // 更新列配置（先删除旧的，再插入新的）
                    @SuppressWarnings("unchecked")
                    GenTable table = (GenTable) result.get("table");
                    @SuppressWarnings("unchecked")
                    List<GenTableColumn> newColumns = (List<GenTableColumn>) result.get("columns");

                    columnRepo.deleteGenTableColumnByTableId(table.getTableId())
                        .flatMap(v -> {
                            for (GenTableColumn col : newColumns) {
                                col.setTableId(table.getTableId());
                            }
                            return columnRepo.insertGenTableColumnBatch(newColumns);
                        })
                        .onSuccess(count -> ok(ctx, Map.of("synced", count)))
                        .onFailure(err -> fail(ctx, err));
                }
            })
            .onFailure(err -> fail(ctx, err));
    }

    // ================================================================
    // HELPER METHODS
    // ================================================================

    /**
     * 解析逗号分隔的ID字符串
     */
    private List<Long> parseIds(String idsStr) {
        return java.util.Arrays.stream(idsStr.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(s -> {
                try { return Long.parseLong(s); }
                catch (NumberFormatException e) { return null; }
            })
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 导入表及其列配置
     */
    private io.vertx.core.Future<Void> importTablesWithColumns(List<GenTable> tables) {
        if (tables.isEmpty()) {
            return Future.succeededFuture();
        }

        io.vertx.core.Promise<Void> promise = Promise.promise();
        java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(0);
        int total = tables.size();

        for (GenTable table : tables) {
            // 插入表
            tableRepo.insertGenTable(table)
                .flatMap(tableId -> {
                    table.setTableId(tableId);
                    // 查询列信息
                    return columnRepo.selectDbTableColumnsByName(table.getTableName())
                        .flatMap(columns -> {
                            // 设置 tableId
                            for (GenTableColumn col : columns) {
                                col.setTableId(tableId);
                            }
                            // 批量插入列
                            return columnRepo.insertGenTableColumnBatch(columns);
                        });
                })
                .onSuccess(v -> {
                    if (counter.incrementAndGet() == total) {
                        promise.complete();
                    }
                })
                .onFailure(promise::fail);
        }

        return promise.future();
    }

    /**
     * 生成预览数据（简化版，实际需要模板引擎）
     */
    private Map<String, String> generatePreview(GenTable table) {
        Map<String, String> preview = new HashMap<>();
        
        // Entity 预览
        StringBuilder entity = new StringBuilder();
        entity.append("package ").append(table.getPackageName()).append(".entity;\n\n");
        entity.append("import io.vertx.core.json.JsonObject;\n");
        entity.append("import io.vertx.sqlclient.Row;\n\n");
        entity.append("public class ").append(table.getClassName()).append(" {\n\n");
        
        for (GenTableColumn col : table.getColumns()) {
            entity.append("    private ").append(col.getJavaType()).append(" ")
                  .append(col.getJavaField()).append(";\n");
        }
        
        entity.append("\n    // Getters & Setters...\n");
        entity.append("}\n");
        
        preview.put("entity.java", entity.toString());
        preview.put("repository.java", "// Repository preview...");
        preview.put("api.java", "// API preview...");
        
        return preview;
    }
}
