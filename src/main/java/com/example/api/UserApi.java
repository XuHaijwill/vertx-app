package com.example.api;

import com.example.core.RequestValidator;
import com.example.service.UserService;
import com.example.service.impl.UserServiceImpl;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.ArrayList;
import java.util.List;

/**
 * User CRUD API — /api/users
 */
public class UserApi extends BaseApi {

    private final UserService userService;

    public UserApi(Vertx vertx) {
        super(vertx);
        this.userService = new UserServiceImpl(vertx);
    }

    @Override
    public void registerRoutes(Router router, String contextPath) {
        router.get(contextPath + "/api/users").handler(this::listOrSearch);
        router.get(contextPath + "/api/users/:id").handler(this::getById);
        router.post(contextPath + "/api/users").handler(this::create);
        router.post(contextPath + "/api/users/batch").handler(this::batchCreate);
        router.put(contextPath + "/api/users/:id").handler(this::update);
        router.put(contextPath + "/api/users/batch").handler(this::batchUpdate);
        router.delete(contextPath + "/api/users/:id").handler(this::delete);
        router.delete(contextPath + "/api/users/batch").handler(this::batchDelete);
    }

    private void listOrSearch(RoutingContext ctx) {
        String q    = queryStr(ctx, "q");
        int page    = queryIntClamped(ctx, "page", 1, 1, 1000);
        int size    = queryIntClamped(ctx, "size", 20, 1, 100);

        if (q != null && !q.isBlank()) {
            respondPaginated(ctx, userService.searchPaginated(q, page, size));
        } else {
            respondPaginated(ctx, userService.findPaginated(page, size));
        }
    }

    private void getById(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) {
            badRequest(ctx, "Invalid user ID");
            return;
        }
        respond(ctx, userService.findById(id));
    }

    private void create(RoutingContext ctx) {
        JsonObject body = bodyJson(ctx);
        RequestValidator.ValidationResult vr = RequestValidator.validateCreateUser(body);
        if (!vr.isValid()) {
            badRequest(ctx, vr.getErrors().toString());
            return;
        }
        respondCreated(ctx, userService.create(body));
    }

    private void update(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) {
            badRequest(ctx, "Invalid user ID");
            return;
        }
        respond(ctx, userService.update(id, bodyJson(ctx)));
    }

    private void delete(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) {
            badRequest(ctx, "Invalid user ID");
            return;
        }
        respondDeleted(ctx, userService.delete(id));
    }

    // ================================================================
    // Batch operations
    // ================================================================

    private void batchCreate(RoutingContext ctx) {
        JsonArray body = ctx.body().asJsonObject().getJsonArray("items");
        if (body == null || body.isEmpty()) {
            badRequest(ctx, "Request body must contain a non-empty 'items' array");
            return;
        }
        List<JsonObject> users = new ArrayList<>();
        for (int i = 0; i < body.size(); i++) {
            users.add(body.getJsonObject(i));
        }
        respond(ctx, userService.batchCreate(users));
    }

    private void batchUpdate(RoutingContext ctx) {
        JsonArray body = ctx.body().asJsonObject().getJsonArray("items");
        if (body == null || body.isEmpty()) {
            badRequest(ctx, "Request body must contain a non-empty 'items' array");
            return;
        }
        List<JsonObject> users = new ArrayList<>();
        for (int i = 0; i < body.size(); i++) {
            users.add(body.getJsonObject(i));
        }
        respond(ctx, userService.batchUpdate(users));
    }

    private void batchDelete(RoutingContext ctx) {
        JsonArray body = ctx.body().asJsonObject().getJsonArray("ids");
        if (body == null || body.isEmpty()) {
            badRequest(ctx, "Request body must contain a non-empty 'ids' array");
            return;
        }
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < body.size(); i++) {
            Long id = parseId(String.valueOf(body.getValue(i)));
            if (id == null) {
                badRequest(ctx, "Invalid id at index " + i);
                return;
            }
            ids.add(id);
        }
        respond(ctx, userService.batchDelete(ids));
    }
}
