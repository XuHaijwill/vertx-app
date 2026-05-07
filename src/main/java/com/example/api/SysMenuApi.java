package com.example.api;

import com.example.auth.RequirePermission;
import com.example.entity.SysMenu;
import com.example.service.SysMenuService;
import com.example.service.impl.SysMenuServiceImpl;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * SysMenu API - REST endpoints for menu management
 */
public class SysMenuApi extends BaseApi {

    private final SysMenuService service;

    public SysMenuApi(Vertx vertx) {
        super(vertx);
        this.service = new SysMenuServiceImpl(vertx);
    }

    @Override
    public void registerRoutes(Router router, String contextPath) {
        // ---- List / Query ----
        // 等效 @PreAuthorize("@ss.hasPermi('system:menu:list')")
        router.get(contextPath + "/api/menus")
            .handler(RequirePermission.of("system:menu:list"))
            .handler(this::listMenus);

        // 等效 @PreAuthorize("@ss.hasPermi('system:menu:query')")
        router.get(contextPath + "/api/menus/tree")
            .handler(RequirePermission.of("system:menu:query"))
            .handler(this::getMenuTree);

        // 等效 @PreAuthorize("@ss.hasPermi('system:menu:query')")
        router.get(contextPath + "/api/menus/:id")
            .handler(RequirePermission.of("system:menu:query"))
            .handler(this::getMenu);

        // 等效 @PreAuthorize("@ss.hasPermi('system:menu:list')")
        router.get(contextPath + "/api/menus/parent/:parentId")
            .handler(RequirePermission.of("system:menu:list"))
            .handler(this::getMenusByParent);

        // 等效 @PreAuthorize("@ss.hasPermi('system:menu:list')")
        router.get(contextPath + "/api/menus/visible")
            .handler(RequirePermission.of("system:menu:list"))
            .handler(this::getVisibleMenus);

        // ---- Write ----
        // 等效 @PreAuthorize("@ss.hasPermi('system:menu:add')")
        router.post(contextPath + "/api/menus")
            .handler(RequirePermission.of("system:menu:add"))
            .handler(this::createMenu);

        // 等效 @PreAuthorize("@ss.hasPermi('system:menu:edit')")
        router.put(contextPath + "/api/menus/:id")
            .handler(RequirePermission.of("system:menu:edit"))
            .handler(this::updateMenu);

        // 等效 @PreAuthorize("@ss.hasPermi('system:menu:remove')")
        router.delete(contextPath + "/api/menus/:id")
            .handler(RequirePermission.of("system:menu:remove"))
            .handler(this::deleteMenu);
    }

    // ================================================================
    // HTTP HANDLERS
    // ================================================================

    private void listMenus(RoutingContext ctx) {
        int page = queryInt(ctx, "page", 1);
        int size = queryIntClamped(ctx, "size", 20, 1, 100);
        respondPaginated(ctx, service.findPaginated(page, size));
    }

    private void getMenuTree(RoutingContext ctx) {
        respond(ctx, service.findMenuTree());
    }

    private void getMenu(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) {
            badRequest(ctx, "Invalid menu ID");
            return;
        }
        respond(ctx, service.findById(id));
    }

    private void getMenusByParent(RoutingContext ctx) {
        Long parentId = parseId(ctx.pathParam("parentId"));
        if (parentId == null) {
            badRequest(ctx, "Invalid parent ID");
            return;
        }
        respond(ctx, service.findByParentId(parentId));
    }

    private void getVisibleMenus(RoutingContext ctx) {
        respond(ctx, service.findVisibleMenus());
    }

    private void createMenu(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            badRequest(ctx, "Request body is required");
            return;
        }
        respondCreated(ctx, service.create(SysMenu.fromJson(body)));
    }

    private void updateMenu(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) {
            badRequest(ctx, "Invalid menu ID");
            return;
        }
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            badRequest(ctx, "Request body is required");
            return;
        }
        respond(ctx, service.update(id, SysMenu.fromJson(body)));
    }

    private void deleteMenu(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) {
            badRequest(ctx, "Invalid menu ID");
            return;
        }
        respondDeleted(ctx, service.delete(id));
    }
}
