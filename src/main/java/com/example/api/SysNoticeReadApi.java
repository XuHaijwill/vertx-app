package com.example.api;

import com.example.entity.SysNoticeRead;
import com.example.repository.SysNoticeReadRepository;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * SysNoticeRead API - REST endpoints for notice read status management
 *
 * 公告已读状态 API，提供：
 * - POST   /api/notices/read                - 标记单条公告已读
 * - POST   /api/notices/read/batch         - 批量标记公告已读
 * - GET    /api/notices/unread-count       - 获取未读公告数量
 * - GET    /api/notices/with-read-status   - 获取带已读状态的公告列表
 * - GET    /api/notices/:id/read-users     - 获取已读某公告的用户列表
 * - GET    /api/notices/:id/is-read        - 检查当前用户是否已读某公告
 * - DELETE /api/notices/read               - 删除公告时清理已读记录
 */
public class SysNoticeReadApi extends BaseApi {

    private final SysNoticeReadRepository repository;

    public SysNoticeReadApi(Vertx vertx) {
        super(vertx);
        this.repository = new SysNoticeReadRepository(vertx);
    }

    @Override
    public void registerRoutes(Router router, String contextPath) {
        router.post(contextPath + "/api/notices/read").handler(this::markAsRead);
        router.post(contextPath + "/api/notices/read/batch").handler(this::markBatchAsRead);
        router.get(contextPath + "/api/notices/unread-count").handler(this::getUnreadCount);
        router.get(contextPath + "/api/notices/with-read-status").handler(this::listWithReadStatus);
        router.get(contextPath + "/api/notices/:id/read-users").handler(this::listReadUsers);
        router.get(contextPath + "/api/notices/:id/is-read").handler(this::checkIsRead);
        router.delete(contextPath + "/api/notices/read").handler(this::deleteReadRecords);
    }

    // ================================================================
    // HTTP HANDLERS
    // ================================================================

    /**
     * 标记单条公告已读
     * POST /api/notices/read
     * Body: { noticeId: 123, userId: 1 }
     *
     * 注意：实际使用中 userId 应从 JWT token 中获取，此处为演示方便
     */
    private void markAsRead(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) { badRequest(ctx, "Request body is required"); return; }

        Long noticeId = body.getLong("noticeId");
        Long userId = body.getLong("userId");

        if (noticeId == null) { badRequest(ctx, "noticeId is required"); return; }
        if (userId == null) { badRequest(ctx, "userId is required"); return; }

        SysNoticeRead record = new SysNoticeRead();
        record.setNoticeId(noticeId);
        record.setUserId(userId);

        respond(ctx, repository.insert(record).map(count ->
            new JsonObject().put("inserted", count)
        ));
    }

    /**
     * 批量标记公告已读
     * POST /api/notices/read/batch
     * Body: { userId: 1, noticeIds: [1, 2, 3] }
     */
    private void markBatchAsRead(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) { badRequest(ctx, "Request body is required"); return; }

        Long userId = body.getLong("userId");
        JsonArray noticeIdsArray = body.getJsonArray("noticeIds");

        if (userId == null) { badRequest(ctx, "userId is required"); return; }
        if (noticeIdsArray == null || noticeIdsArray.isEmpty()) {
            badRequest(ctx, "noticeIds is required and cannot be empty");
            return;
        }

        Long[] noticeIds = new Long[noticeIdsArray.size()];
        for (int i = 0; i < noticeIdsArray.size(); i++) {
            noticeIds[i] = noticeIdsArray.getLong(i);
        }

        respond(ctx, repository.insertBatch(userId, noticeIds).map(count ->
            new JsonObject().put("inserted", count)
        ));
    }

    /**
     * 获取未读公告数量
     * GET /api/notices/unread-count?userId=1
     */
    private void getUnreadCount(RoutingContext ctx) {
        Long userId = parseId(queryStr(ctx, "userId"));
        if (userId == null) { badRequest(ctx, "userId is required"); return; }

        respond(ctx, repository.countUnread(userId).map(count ->
            new JsonObject().put("unreadCount", count)
        ));
    }

    /**
     * 获取带已读状态的公告列表
     * GET /api/notices/with-read-status?userId=1&limit=20
     */
    private void listWithReadStatus(RoutingContext ctx) {
        Long userId = parseId(queryStr(ctx, "userId"));
        if (userId == null) { badRequest(ctx, "userId is required"); return; }

        int limit = queryIntClamped(ctx, "limit", 20, 1, 100);

        respond(ctx, repository.findNoticesWithReadStatus(userId, limit));
    }

    /**
     * 获取已读某公告的用户列表
     * GET /api/notices/:id/read-users?searchValue=xxx
     */
    private void listReadUsers(RoutingContext ctx) {
        Long noticeId = parseId(ctx.pathParam("id"));
        if (noticeId == null) { badRequest(ctx, "Invalid notice ID"); return; }

        String searchValue = queryStr(ctx, "searchValue");

        respond(ctx, repository.findReadUsers(noticeId, searchValue));
    }

    /**
     * 检查当前用户是否已读某公告
     * GET /api/notices/:id/is-read?userId=1
     */
    private void checkIsRead(RoutingContext ctx) {
        Long noticeId = parseId(ctx.pathParam("id"));
        if (noticeId == null) { badRequest(ctx, "Invalid notice ID"); return; }

        Long userId = parseId(queryStr(ctx, "userId"));
        if (userId == null) { badRequest(ctx, "userId is required"); return; }

        respond(ctx, repository.isRead(noticeId, userId).map(isRead ->
            new JsonObject().put("isRead", isRead)
        ));
    }

    /**
     * 删除公告时清理已读记录
     * DELETE /api/notices/read?ids=1,2,3
     */
    private void deleteReadRecords(RoutingContext ctx) {
        String idsParam = queryStr(ctx, "ids");
        if (idsParam == null || idsParam.isBlank()) {
            badRequest(ctx, "ids parameter is required");
            return;
        }

        String[] idStrs = idsParam.split(",");
        Long[] ids = new Long[idStrs.length];
        for (int i = 0; i < idStrs.length; i++) {
            try {
                ids[i] = Long.parseLong(idStrs[i].trim());
            } catch (NumberFormatException e) {
                badRequest(ctx, "Invalid ID format: " + idStrs[i]);
                return;
            }
        }

        respond(ctx, repository.deleteByNoticeIds(ids).map(count ->
            new JsonObject().put("deleted", count)
        ));
    }
}
