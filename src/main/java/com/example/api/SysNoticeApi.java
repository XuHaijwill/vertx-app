package com.example.api;

import com.example.entity.SysNotice;
import com.example.repository.SysNoticeRepository;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.List;

/**
 * SysNotice API - REST endpoints for notice/announcement management
 *
 * 对应 RuoYi SysNoticeController，提供相同的 HTTP 接口：
 * - GET    /api/notices           - 查询公告列表（分页）
 * - GET    /api/notices/:id       - 查询公告详情
 * - POST   /api/notices           - 新增公告
 * - PUT    /api/notices/:id       - 修改公告
 * - DELETE /api/notices/:id       - 删除单条公告
 * - DELETE /api/notices           - 批量删除公告（传 ids 参数）
 */
public class SysNoticeApi extends BaseApi {

    private final SysNoticeRepository repository;

    public SysNoticeApi(Vertx vertx) {
        super(vertx);
        this.repository = new SysNoticeRepository(vertx);
    }

    @Override
    public void registerRoutes(Router router, String contextPath) {
        router.get(contextPath + "/api/notices").handler(this::listNotices);
        router.get(contextPath + "/api/notices/:id").handler(this::getNotice);
        router.post(contextPath + "/api/notices").handler(this::createNotice);
        router.put(contextPath + "/api/notices/:id").handler(this::updateNotice);
        router.delete(contextPath + "/api/notices/:id").handler(this::deleteNotice);
        router.delete(contextPath + "/api/notices").handler(this::batchDeleteNotices);
    }

    // ================================================================
    // HTTP HANDLERS
    // ================================================================

    /**
     * 查询公告列表（分页）
     * GET /api/notices?noticeTitle=xxx&noticeType=1&status=0&page=1&size=20
     */
    private void listNotices(RoutingContext ctx) {
        String noticeTitle = queryStr(ctx, "noticeTitle");
        String noticeType  = queryStr(ctx, "noticeType");
        String status      = queryStr(ctx, "status");
        int page = queryInt(ctx, "page", 1);
        int size = queryIntClamped(ctx, "size", 20, 1, 100);

        SysNotice query = new SysNotice();
        query.setNoticeTitle(noticeTitle);
        query.setNoticeType(noticeType);
        query.setStatus(status);

        // 先查总数，再查分页数据
        repository.searchCount(query)
            .flatMap(total -> repository.searchPaginated(query, page, size)
                .map(list -> {
                    com.example.core.PageResult<SysNotice> pageResult =
                        new com.example.core.PageResult<>(list, total, page, size);
                    return pageResult;
                }))
            .onSuccess(pageResult -> {
                ctx.json(com.example.core.ApiResponse.success(pageResult.toJson()).toJson());
            })
            .onFailure(err -> fail(ctx, err));
    }

    /**
     * 查询公告详情
     * GET /api/notices/:id
     */
    private void getNotice(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) { badRequest(ctx, "Invalid notice ID"); return; }
        respond(ctx, repository.findById(id));
    }

    /**
     * 新增公告
     * POST /api/notices
     * Body: { noticeTitle, noticeType, noticeContent, status, remark }
     */
    private void createNotice(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) { badRequest(ctx, "Request body is required"); return; }

        SysNotice notice = SysNotice.fromJson(body);

        // 校验必填字段
        if (notice.getNoticeTitle() == null || notice.getNoticeTitle().isBlank()) {
            badRequest(ctx, "noticeTitle is required");
            return;
        }
        if (notice.getNoticeType() == null || notice.getNoticeType().isBlank()) {
            badRequest(ctx, "noticeType is required");
            return;
        }

        respondCreated(ctx, repository.create(notice));
    }

    /**
     * 修改公告
     * PUT /api/notices/:id
     * Body: { noticeTitle, noticeType, noticeContent, status, remark }
     */
    private void updateNotice(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) { badRequest(ctx, "Invalid notice ID"); return; }

        JsonObject body = ctx.body().asJsonObject();
        if (body == null) { badRequest(ctx, "Request body is required"); return; }

        SysNotice notice = SysNotice.fromJson(body);
        notice.setNoticeId(id);

        respond(ctx, repository.update(notice));
    }

    /**
     * 删除单条公告
     * DELETE /api/notices/:id
     */
    private void deleteNotice(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) { badRequest(ctx, "Invalid notice ID"); return; }
        respondDeleted(ctx, repository.deleteById(id));
    }

    /**
     * 批量删除公告
     * DELETE /api/notices?ids=1,2,3
     */
    private void batchDeleteNotices(RoutingContext ctx) {
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

        respond(ctx, repository.deleteByIds(ids).map(count ->
            new JsonObject().put("deleted", count)
        ));
    }
}
