package com.example.api;

import com.example.core.ApiResponse;
import com.example.core.PageResult;
import com.example.repository.AuditRepository;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.List;

/**
 * AuditApi — REST endpoints for audit log query.
 *
 * <p>All routes require authentication (audit logs are sensitive).
 * All parameters are optional — missing params = no filter.
 *
 * <p>Query filters:
 * <pre>
 * GET /api/audit-logs?entityType=orders&entityId=1&action=AUDIT_UPDATE
 * GET /api/audit-logs?userId=1&from=2026-04-01&to=2026-04-30
 * GET /api/audit-logs?status=FAILURE&page=1&size=20
 * </pre>
 */
public class AuditApi extends BaseApi {

    private final AuditRepository auditRepo;

    public AuditApi(Vertx vertx) {
        super(vertx);
        this.auditRepo = new AuditRepository(vertx);
    }

    @Override
    public void registerRoutes(Router router, String contextPath) {
        String prefix = contextPath == null ? "/api" : contextPath;

        // Search / list with filters
        router.get(prefix + "/audit-logs").handler(this::search);

        // Query by entity ID (convenience shortcut)
        router.get(prefix + "/audit-logs/entity/:entityType/:entityId").handler(this::getByEntity);

        // Query by user ID (convenience shortcut)
        router.get(prefix + "/audit-logs/user/:userId").handler(this::getByUser);

        // Get single audit record
        router.get(prefix + "/audit-logs/:id").handler(this::getById);

        // Summary statistics
        router.get(prefix + "/audit-logs/stats/summary").handler(this::summary);

        // Archive statistics
        router.get(prefix + "/audit-logs/stats/archive").handler(this::archiveStats);

        // Manual archive trigger (admin only)
        router.post(prefix + "/audit-logs/archive").handler(this::triggerArchive);
    }

    // ================================================================
    // GET /api/audit-logs (search with filters + pagination)
    // ================================================================

    private void search(RoutingContext ctx) {
        String entityType = queryStr(ctx, "entityType");
        String entityId   = queryStr(ctx, "entityId");
        String action     = queryStr(ctx, "action");
        String status     = queryStr(ctx, "status");
        String username   = queryStr(ctx, "username");
        String from       = queryStr(ctx, "from");
        String to         = queryStr(ctx, "to");
        int page = queryIntClamped(ctx, "page", 1, 1, Integer.MAX_VALUE);
        int size = queryIntClamped(ctx, "size", 20, 1, 200);

        auditRepo.search(entityType, entityId, null, action, status, username, from, to, page, size)
            .compose(list -> {
                return auditRepo.searchCount(entityType, entityId, null, action, status, username, from, to)
                    .map(count -> new PageResult<JsonObject>(list, count, page, size));
            })
            .onSuccess(r -> ctx.json(ApiResponse.success(r.toJson()).toJson()))
            .onFailure(err -> fail(ctx, err));
    }

    // ================================================================
    // GET /api/audit-logs/entity/:entityType/:entityId
    // ================================================================

    private void getByEntity(RoutingContext ctx) {
        String entityType = ctx.pathParam("entityType");
        String entityId   = ctx.pathParam("entityId");
        int limit = queryIntClamped(ctx, "limit", 50, 1, 200);

        auditRepo.findByEntity(entityType, entityId, limit)
            .onSuccess(list -> ctx.json(ApiResponse.success(list).toJson()))
            .onFailure(err -> fail(ctx, err));
    }

    // ================================================================
    // GET /api/audit-logs/user/:userId
    // ================================================================

    private void getByUser(RoutingContext ctx) {
        Long userId = parseId(ctx.pathParam("userId"));
        if (userId == null) { badRequest(ctx, "Invalid userId"); return; }

        int limit = queryIntClamped(ctx, "limit", 50, 1, 200);
        auditRepo.findByUser(userId, limit)
            .onSuccess(list -> ctx.json(ApiResponse.success(list).toJson()))
            .onFailure(err -> fail(ctx, err));
    }

    // ================================================================
    // GET /api/audit-logs/:id
    // ================================================================

    private void getById(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) { badRequest(ctx, "Invalid id"); return; }

        auditRepo.findById(id)
            .onSuccess(record -> {
                if (record == null) notFound(ctx, "Audit log not found: " + id);
                else ctx.json(ApiResponse.success(record).toJson());
            })
            .onFailure(err -> fail(ctx, err));
    }

    // ================================================================
    // GET /api/audit-logs/stats/summary (count by action in last N days)
    // ================================================================

    private void summary(RoutingContext ctx) {
        int days = queryIntClamped(ctx, "days", 7, 1, 90);

        auditRepo.findByAction("AUDIT_CREATE", 1)
            .compose(createList -> {
                // Quick summary: count each action type using search
                return auditRepo.searchCount(null, null, null, "AUDIT_CREATE", null, null, null, null)
                    .compose(createCount ->
                        auditRepo.searchCount(null, null, null, "AUDIT_UPDATE", null, null, null, null)
                            .compose(updateCount ->
                                auditRepo.searchCount(null, null, null, "AUDIT_DELETE", null, null, null, null)
                                    .map(deleteCount -> new JsonObject()
                                        .put("total", createCount + updateCount + deleteCount)
                                        .put("AUDIT_CREATE", createCount)
                                        .put("AUDIT_UPDATE", updateCount)
                                        .put("AUDIT_DELETE", deleteCount)
                                    )
                            )
                    );
            })
            .onSuccess(stats -> ctx.json(ApiResponse.success(stats).toJson()))
            .onFailure(err -> fail(ctx, err));
    }

    // ================================================================
    // GET /api/audit-logs/stats/archive (archive statistics)
    // ================================================================

    private void archiveStats(RoutingContext ctx) {
        auditRepo.getArchiveStats()
            .onSuccess(stats -> ctx.json(ApiResponse.success(stats).toJson()))
            .onFailure(err -> fail(ctx, err));
    }

    // ================================================================
    // POST /api/audit-logs/archive (manual archive trigger)
    // ================================================================

    private void triggerArchive(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        int monthsOld = (body != null && body.containsKey("monthsOld"))
            ? body.getInteger("monthsOld") : 6;
        int batchSize = (body != null && body.containsKey("batchSize"))
            ? body.getInteger("batchSize") : 10000;

        auditRepo.archiveOldLogs(monthsOld, batchSize)
            .onSuccess(count -> {
                JsonObject result = new JsonObject()
                    .put("archivedCount", count)
                    .put("monthsOld", monthsOld);
                ctx.json(ApiResponse.success(result).toJson());
            })
            .onFailure(err -> fail(ctx, err));
    }

    // 使用 BaseApi 的 protected fail 方法，无需重复定义
}
