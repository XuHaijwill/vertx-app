package com.example.api;

import com.example.entity.SysConfig;
import com.example.service.SysConfigService;
import com.example.service.impl.SysConfigServiceImpl;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.ArrayList;
import java.util.List;

/**
 * SysConfig API - REST endpoints for system configuration management
 */
public class SysConfigApi extends BaseApi {

    private final SysConfigService service;

    public SysConfigApi(Vertx vertx) {
        super(vertx);
        this.service = new SysConfigServiceImpl(vertx);
    }

    @Override
    public void registerRoutes(Router router, String contextPath) {
        router.get(contextPath + "/api/sys-configs").handler(this::listConfigs);
        router.get(contextPath + "/api/sys-configs/:id").handler(this::getConfig);
        router.get(contextPath + "/api/sys-configs/key/:configKey").handler(this::getConfigByKey);
        router.post(contextPath + "/api/sys-configs").handler(this::createConfig);
        router.put(contextPath + "/api/sys-configs/:id").handler(this::updateConfig);
        router.delete(contextPath + "/api/sys-configs/:id").handler(this::deleteConfig);
    }

    // ================================================================
    // HTTP HANDLERS
    // ================================================================

    private void listConfigs(RoutingContext ctx) {
        String configName = queryStr(ctx, "configName");
        String configKey  = queryStr(ctx, "configKey");
        String group      = queryStr(ctx, "group");
        int page = queryInt(ctx, "page", 1);
        int size = queryIntClamped(ctx, "size", 20, 1, 100);

        boolean hasFilters = configName != null || configKey != null || group != null;

        if (hasFilters) {
            respondPaginated(ctx, service.searchPaginated(configName, configKey, group, page, size));
        } else {
            respondPaginated(ctx, service.findPaginated(page, size));
        }
    }

    private void getConfig(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) { badRequest(ctx, "Invalid config ID"); return; }
        respond(ctx, service.findById(id));
    }

    private void getConfigByKey(RoutingContext ctx) {
        String configKey = ctx.pathParam("configKey");
        if (configKey == null || configKey.isBlank()) { badRequest(ctx, "configKey is required"); return; }
        respond(ctx, service.findByConfigKey(configKey));
    }

    private void createConfig(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) { badRequest(ctx, "Request body is required"); return; }
        respondCreated(ctx, service.create(SysConfig.fromJson(body)));
    }

    private void updateConfig(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) { badRequest(ctx, "Invalid config ID"); return; }
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) { badRequest(ctx, "Request body is required"); return; }
        respond(ctx, service.update(id, SysConfig.fromJson(body)));
    }

    private void deleteConfig(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) { badRequest(ctx, "Invalid config ID"); return; }
        respondDeleted(ctx, service.delete(id));
    }
}
