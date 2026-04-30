package com.example.api;

import com.example.core.PageResult;
import com.example.repository.SysConfigRepository;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.List;

/**
 * SysConfig API - REST endpoints for system configuration management
 */
public class SysConfigApi extends BaseApi {

    private final SysConfigRepository repo;

    public SysConfigApi(Vertx vertx) {
        super(vertx);
        this.repo = new SysConfigRepository(vertx);
    }

    @Override
    public void registerRoutes(Router router, String contextPath) {
        // GET /api/sys-configs - List all configs (supports query params)
        router.get(contextPath + "/api/sys-configs").handler(this::listConfigs);
        
        // GET /api/sys-configs/:id - Get config by ID
        router.get(contextPath + "/api/sys-configs/:id").handler(this::getConfig);
        
        // GET /api/sys-configs/key/:configKey - Get config by key
        router.get(contextPath + "/api/sys-configs/key/:configKey").handler(this::getConfigByKey);
    }

    // ================================================================
    // HTTP HANDLERS
    // ================================================================

    /**
     * GET /api/sys-configs
     * Query params:
     *   - configName: fuzzy search on config_name (supports multi-condition LIKE)
     *   - configValue: fuzzy search on config_value (supports multi-condition LIKE)
     *   - configKey: exact match on config_key
     *   - configType: exact match on config_type (Y/N)
     *   - page: page number (default: 1)
     *   - size: page size (default: 20, max: 100)
     * 
     * Example: GET /api/sys-configs?configName=skin&configValue=blue
     *   Returns configs where config_name LIKE '%skin%' AND config_value LIKE '%blue%'
     */
    private void listConfigs(RoutingContext ctx) {
        String configName = queryStr(ctx, "configName");
        String configValue = queryStr(ctx, "configValue");
        String configKey = queryStr(ctx, "configKey");
        String configType = queryStr(ctx, "configType");
        int page = queryInt(ctx, "page", 1);
        int size = queryIntClamped(ctx, "size", 20, 1, 100);

        // Check if any filter is provided
        boolean hasFilters = configName != null || configValue != null 
            || configKey != null || configType != null;

        if (hasFilters) {
            // Advanced search with filters - return paginated results
            respondPaginated(ctx, repo.searchPaginated(configName, configKey, configValue, configType, page, size)
                .compose(list -> repo.searchCount(configName, configKey, configValue, configType)
                    .map(count -> new PageResult<>(list, count, page, size))));
        } else {
            // Simple pagination (no filters)
            respondPaginated(ctx, repo.findPaginated(page, size)
                .compose(list -> repo.count()
                    .map(count -> new PageResult<>(list, count, page, size))));
        }
    }

    /**
     * GET /api/sys-configs/:id
     */
    private void getConfig(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) {
            badRequest(ctx, "Invalid config ID");
            return;
        }
        
        repo.findById(id)
            .onSuccess(config -> {
                if (config == null) {
                    notFound(ctx, "Config not found: " + id);
                } else {
                    ok(ctx, config);
                }
            })
            .onFailure(err -> fail(ctx, err));
    }

    /**
     * GET /api/sys-configs/key/:configKey
     */
    private void getConfigByKey(RoutingContext ctx) {
        String configKey = ctx.pathParam("configKey");
        if (configKey == null || configKey.isBlank()) {
            badRequest(ctx, "config_key is required");
            return;
        }
        
        repo.findByConfigKey(configKey)
            .onSuccess(config -> {
                if (config == null) {
                    notFound(ctx, "Config not found: " + configKey);
                } else {
                    ok(ctx, config);
                }
            })
            .onFailure(err -> fail(ctx, err));
    }
}
