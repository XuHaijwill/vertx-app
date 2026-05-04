package com.example.api;

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
        router.get(contextPath + "/api/menus").handler(this::listMenus);
        router.get(contextPath + "/api/menus/tree").handler(this::getMenuTree);
        router.get(contextPath + "/api/menus/:id").handler(this::getMenu);
        router.get(contextPath + "/api/menus/parent/:parentId").handler(this::getMenusByParent);
        router.get(contextPath + "/api/menus/visible").handler(this::getVisibleMenus);
        router.post(contextPath + "/api/menus").handler(this::createMenu);
        router.put(contextPath + "/api/menus/:id").handler(this::updateMenu);
        router.delete(contextPath + "/api/menus/:id").handler(this::deleteMenu);
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
        respondCreated(ctx, service.create(body));
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
        respond(ctx, service.update(id, body));
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
