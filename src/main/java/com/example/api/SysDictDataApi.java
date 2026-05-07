package com.example.api;

import com.example.core.PageResult;
import com.example.entity.SysDictData;
import com.example.service.SysDictDataService;
import com.example.service.impl.SysDictDataServiceImpl;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * SysDictData API - REST endpoints for dictionary data management
 */
public class SysDictDataApi extends BaseApi {

    private final SysDictDataService service;

    public SysDictDataApi(Vertx vertx) {
        super(vertx);
        this.service = new SysDictDataServiceImpl(vertx);
    }

    @Override
    public void registerRoutes(Router router, String contextPath) {
        router.get(contextPath + "/api/dict-data").handler(this::listDictData);
        router.get(contextPath + "/api/dict-data/:id").handler(this::getDictData);
        router.get(contextPath + "/api/dict-data/type/:dictType").handler(this::getDictDataByType);
        router.post(contextPath + "/api/dict-data").handler(this::createDictData);
        router.put(contextPath + "/api/dict-data/:id").handler(this::updateDictData);
        router.delete(contextPath + "/api/dict-data/:id").handler(this::deleteDictData);
        router.delete(contextPath + "/api/dict-data/type/:dictType").handler(this::deleteDictDataByType);
    }

    // ================================================================
    // HTTP HANDLERS
    // ================================================================

    private void listDictData(RoutingContext ctx) {
        String dictType = queryStr(ctx, "dictType");
        String dictLabel = queryStr(ctx, "dictLabel");
        String status = queryStr(ctx, "status");
        int page = queryInt(ctx, "page", 1);
        int size = queryIntClamped(ctx, "size", 20, 1, 100);

        boolean hasFilters = dictType != null || dictLabel != null || status != null;

        if (hasFilters) {
            respondPaginated(ctx, service.searchPaginated(dictType, dictLabel, status, page, size));
        } else {
            respondPaginated(ctx, service.findPaginated(page, size));
        }
    }

    private void getDictData(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) {
            badRequest(ctx, "Invalid dictionary data ID");
            return;
        }
        respond(ctx, service.findById(id));
    }

    private void getDictDataByType(RoutingContext ctx) {
        String dictType = ctx.pathParam("dictType");
        if (dictType == null || dictType.isBlank()) {
            badRequest(ctx, "dictType is required");
            return;
        }
        respond(ctx, service.findByDictType(dictType));
    }

    private void createDictData(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            badRequest(ctx, "Request body is required");
            return;
        }
        respondCreated(ctx, service.create(SysDictData.fromJson(body)));
    }

    private void updateDictData(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) {
            badRequest(ctx, "Invalid dictionary data ID");
            return;
        }
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            badRequest(ctx, "Request body is required");
            return;
        }
        respond(ctx, service.update(id, SysDictData.fromJson(body)));
    }

    private void deleteDictData(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) {
            badRequest(ctx, "Invalid dictionary data ID");
            return;
        }
        respondDeleted(ctx, service.delete(id));
    }

    private void deleteDictDataByType(RoutingContext ctx) {
        String dictType = ctx.pathParam("dictType");
        if (dictType == null || dictType.isBlank()) {
            badRequest(ctx, "dictType is required");
            return;
        }
        respondDeleted(ctx, service.deleteByDictType(dictType));
    }
}
