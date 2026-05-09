package com.example.api;

import com.example.core.ApiResponse;
import com.example.core.PageResult;
import com.example.entity.AccessLog;
import com.example.service.AccessLogService;
import com.example.service.impl.AccessLogServiceImpl;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.List;

/**
 * AccessLogApi — 访问日志 REST 端点
 *
 * <p>提供访问日志的查询、统计和清理功能。
 * 所有端点需要认证。
 *
 * <h3>端点列表：</h3>
 * <pre>
 * GET    /api/access-logs              — 搜索（支持多条件 + 分页）
 * GET    /api/access-logs/:id          — 按 ID 查询单条
 * GET    /api/access-logs/user/:userId — 按用户查询
 * GET    /api/access-logs/stats        — 概览统计
 * GET    /api/access-logs/stats/paths  — Top 路径
 * GET    /api/access-logs/stats/status — 状态码分布
 * GET    /api/access-logs/retention    — 获取保留天数
 * PUT    /api/access-logs/retention    — 设置保留天数
 * DELETE /api/access-logs/cleanup      — 手动清理过期日志
 * </pre>
 */
public class AccessLogApi extends BaseApi {

    private final AccessLogService service;

    public AccessLogApi(Vertx vertx) {
        super(vertx);
        this.service = new AccessLogServiceImpl(vertx);
    }

    @Override
    public void registerRoutes(Router router, String contextPath) {
        String base = contextPath + "/api/access-logs";

        // Search with filters + pagination
        router.get(base).handler(this::search);

        // Get single record
        router.get(base + "/:id").handler(this::getById);

        // Query by user
        router.get(base + "/user/:userId").handler(this::getByUser);

        // Stats
        router.get(base + "/stats/summary").handler(this::statsSummary);
        router.get(base + "/stats/paths").handler(this::statsTopPaths);
        router.get(base + "/stats/status").handler(this::statsStatusCode);

        // Retention config
        router.get(base + "/retention").handler(this::getRetention);
        router.put(base + "/retention").handler(this::setRetention);

        // Manual cleanup
        router.delete(base + "/cleanup").handler(this::manualCleanup);
    }

    // ================================================================
    // GET /api/access-logs
    // ================================================================

    private void search(RoutingContext ctx) {
        Long userId     = parseIdParam(ctx, "userId");
        String username = queryStr(ctx, "username");
        String method   = queryStr(ctx, "method");
        String path     = queryStr(ctx, "path");
        Integer statusCode = queryIntNullable(ctx, "statusCode");
        String userIp   = queryStr(ctx, "userIp");
        String from     = queryStr(ctx, "from");
        String to       = queryStr(ctx, "to");
        int page = queryIntClamped(ctx, "page", 1, 1, Integer.MAX_VALUE);
        int size = queryIntClamped(ctx, "size", 20, 1, 200);

        respondPaginated(ctx, service.search(
            userId, username, method, path, statusCode, userIp,
            from, to, page, size));
    }

    // ================================================================
    // GET /api/access-logs/:id
    // ================================================================

    private void getById(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) { badRequest(ctx, "Invalid id"); return; }

        service.findById(id)
            .onSuccess(log -> {
                if (log == null) notFound(ctx, "Access log not found: " + id);
                else ok(ctx, log.toJson());
            })
            .onFailure(err -> fail(ctx, err));
    }

    // ================================================================
    // GET /api/access-logs/user/:userId
    // ================================================================

    private void getByUser(RoutingContext ctx) {
        Long userId = parseId(ctx.pathParam("userId"));
        if (userId == null) { badRequest(ctx, "Invalid userId"); return; }

        int limit = queryIntClamped(ctx, "limit", 50, 1, 200);
        service.findByUser(userId, limit)
            .onSuccess(list -> ok(ctx, toJsonList(list)))
            .onFailure(err -> fail(ctx, err));
    }

    // ================================================================
    // GET /api/access-logs/stats/summary
    // ================================================================

    private void statsSummary(RoutingContext ctx) {
        int days = queryIntClamped(ctx, "days", 7, 1, 90);
        service.getStats(days)
            .onSuccess(stats -> ok(ctx, stats))
            .onFailure(err -> fail(ctx, err));
    }

    // ================================================================
    // GET /api/access-logs/stats/paths
    // ================================================================

    private void statsTopPaths(RoutingContext ctx) {
        int days = queryIntClamped(ctx, "days", 7, 1, 90);
        int limit = queryIntClamped(ctx, "limit", 10, 1, 100);
        service.getTopPaths(days, limit)
            .onSuccess(list -> ok(ctx, list))
            .onFailure(err -> fail(ctx, err));
    }

    // ================================================================
    // GET /api/access-logs/stats/status
    // ================================================================

    private void statsStatusCode(RoutingContext ctx) {
        int days = queryIntClamped(ctx, "days", 7, 1, 90);
        service.getStatusCodeStats(days)
            .onSuccess(list -> ok(ctx, list))
            .onFailure(err -> fail(ctx, err));
    }

    // ================================================================
    // GET /api/access-logs/retention
    // ================================================================

    private void getRetention(RoutingContext ctx) {
        service.getRetentionDays()
            .onSuccess(days -> ok(ctx, new JsonObject()
                .put("retentionDays", days)
                .put("configKey", "sys.access-log.retentionDays")))
            .onFailure(err -> fail(ctx, err));
    }

    // ================================================================
    // PUT /api/access-logs/retention
    // ================================================================

    private void setRetention(RoutingContext ctx) {
        JsonObject body = bodyJson(ctx);
        if (body == null || !body.containsKey("retentionDays")) {
            badRequest(ctx, "retentionDays is required");
            return;
        }

        int days;
        try {
            days = body.getInteger("retentionDays");
        } catch (ClassCastException e) {
            badRequest(ctx, "retentionDays must be an integer");
            return;
        }

        if (days < 0) {
            badRequest(ctx, "retentionDays must be >= 0 (0 = disable auto-cleanup)");
            return;
        }

        // Update sys_config directly
        com.example.repository.SysConfigRepository configRepo =
            new com.example.repository.SysConfigRepository(vertx);
        configRepo.findByConfigKey("sys.access-log.retentionDays")
            .compose(config -> {
                if (config == null) {
                    return Future.failedFuture("Config key 'sys.access-log.retentionDays' not found");
                }
                config.setConfigValue(String.valueOf(days));
                return configRepo.update(config.getConfigId(), config);
            })
            .onSuccess(updated -> ok(ctx, new JsonObject()
                .put("retentionDays", days)
                .put("message", days == 0 ? "Auto-cleanup disabled" :
                    "Logs older than " + days + " days will be auto-deleted")))
            .onFailure(err -> fail(ctx, err));
    }

    // ================================================================
    // DELETE /api/access-logs/cleanup
    // ================================================================

    private void manualCleanup(RoutingContext ctx) {
        int retentionDays = queryIntClamped(ctx, "retentionDays", -1, 0, 3650);

        io.vertx.core.Future<Long> cleanupFuture;
        if (retentionDays > 0) {
            cleanupFuture = service.cleanupOlderThan(retentionDays);
        } else {
            // Use configured retention days
            cleanupFuture = service.cleanupExpired();
        }

        cleanupFuture
            .onSuccess(count -> ok(ctx, new JsonObject()
                .put("deletedCount", count)
                .put("message", count + " expired log records deleted")))
            .onFailure(err -> fail(ctx, err));
    }

    // ================================================================
    // Helpers
    // ================================================================

    private Long parseIdParam(RoutingContext ctx, String param) {
        String val = queryStr(ctx, param);
        if (val == null || val.isBlank()) return null;
        return parseId(val);
    }

    private Integer queryIntNullable(RoutingContext ctx, String param) {
        String val = queryStr(ctx, param);
        if (val == null || val.isBlank()) return null;
        try { return Integer.parseInt(val); }
        catch (NumberFormatException e) { return null; }
    }

    private List<JsonObject> toJsonList(List<AccessLog> list) {
        List<JsonObject> result = new java.util.ArrayList<>();
        for (AccessLog log : list) result.add(log.toJson());
        return result;
    }
}
