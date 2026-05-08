package com.example.api;

import com.example.core.PageResult;
import com.example.entity.SysDictType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.service.SysDictTypeService;
import com.example.service.impl.SysDictTypeServiceImpl;
import io.vertx.core.Future;
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

    private static final Logger LOG = LoggerFactory.getLogger(SysDictTypeApi.class);

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
        router.delete(contextPath + "/api/dict-types/refreshCache").handler(this::refreshCache);
        router.get(contextPath + "/api/dict-types/optionselect").handler(this::optionselect);
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
        String idsParam = ctx.pathParam("id");
        if (idsParam == null || idsParam.isBlank()) {
            badRequest(ctx, "Invalid dictionary type ID");
            return;
        }

        // 支持批量删除：逗号分隔的 ID 列表
        String[] idStrs = idsParam.split(",");
        List<Future<Void>> futures = new ArrayList<>();
        for (String idStr : idStrs) {
            Long id = parseId(idStr.trim());
            if (id != null) {
                futures.add(service.delete(id));
            }
        }

        if (futures.isEmpty()) {
            badRequest(ctx, "No valid ID provided");
            return;
        }

        io.vertx.core.Future.all(futures).onComplete(ar -> {
            if (ar.succeeded()) {
                ok(ctx, new JsonObject().put("msg", "delete success"));
            } else {
                fail(ctx, new Exception("Delete failed"));
            }
        });
    }

    private void refreshCache(RoutingContext ctx) {
        // 字典缓存刷新：重新加载所有字典类型到内存
        service.findAll().onComplete(ar -> {
            if (ar.succeeded()) {
                List<SysDictType> all = ar.result();
                LOG.info("Dictionary cache refreshed, {} types loaded", all.size());
                ok(ctx, new JsonObject().put("msg", "refresh success").put("count", all.size()));
            } else {
                fail(ctx, ar.cause());
            }
        });
    }

    private void optionselect(RoutingContext ctx) {
        // 获取所有字典类型（不分页），供下拉框使用
        respond(ctx, service.findAll());
    }
}
