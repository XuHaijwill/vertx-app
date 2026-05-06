package com.example.api;

import com.example.core.PageResult;
import com.example.entity.SysDictType;
import com.example.service.SysDictTypeService;
import com.example.service.impl.SysDictTypeServiceImpl;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.ArrayList;
import java.util.List;

/**
 * SysDictType API - REST endpoints for dictionary type management
 */
public class SysDictTypeApi extends BaseApi {

    private final SysDictTypeService service;

    public SysDictTypeApi(Vertx vertx) {
        super(vertx);
        this.service = new SysDictTypeServiceImpl(vertx);
    }

    @Override
    public void registerRoutes(Router router, String contextPath) {
        router.get(contextPath + "/api/dict-types").handler(this::listDictTypes);
        router.get(contextPath + "/api/dict-types/:id").handler(this::getDictType);
        router.get(contextPath + "/api/dict-types/type/:dictType").handler(this::getDictTypeByType);
        router.post(contextPath + "/api/dict-types").handler(this::createDictType);
        router.put(contextPath + "/api/dict-types/:id").handler(this::updateDictType);
        router.delete(contextPath + "/api/dict-types/:id").handler(this::deleteDictType);
    }

    // ================================================================
    // HTTP HANDLERS
    // ================================================================

    private void listDictTypes(RoutingContext ctx) {
        String dictName = queryStr(ctx, "dictName");
        String dictType = queryStr(ctx, "dictType");
        String status = queryStr(ctx, "status");
        int page = queryInt(ctx, "page", 1);
        int size = queryIntClamped(ctx, "size", 20, 1, 100);

        boolean hasFilters = dictName != null || dictType != null || status != null;

        if (hasFilters) {
            respondPaginated(ctx, service.searchPaginated(dictName, dictType, status, page, size));
        } else {
            respondPaginated(ctx, service.findPaginated(page, size));
        }
    }

    private void getDictType(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) {
            badRequest(ctx, "Invalid dictionary type ID");
            return;
        }
        respond(ctx, service.findById(id));
    }

    private void getDictTypeByType(RoutingContext ctx) {
        String dictType = ctx.pathParam("dictType");
        if (dictType == null || dictType.isBlank()) {
            badRequest(ctx, "dict_type is required");
            return;
        }
        respond(ctx, service.findByDictType(dictType));
    }

    private void createDictType(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            badRequest(ctx, "Request body is required");
            return;
        }
        respondCreated(ctx, service.create(SysDictType.fromJson(body)));
    }

    private void updateDictType(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) {
            badRequest(ctx, "Invalid dictionary type ID");
            return;
        }
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            badRequest(ctx, "Request body is required");
            return;
        }
        respond(ctx, service.update(id, SysDictType.fromJson(body)));
    }

    private void deleteDictType(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) {
            badRequest(ctx, "Invalid dictionary type ID");
            return;
        }
        respondDeleted(ctx, service.delete(id));
    }
}
