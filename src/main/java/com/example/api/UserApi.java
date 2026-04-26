package com.example.api;

import com.example.core.BusinessException;
import com.example.core.RequestValidator;
import com.example.service.UserService;
import com.example.service.UserServiceImpl;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

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
    public void registerRoutes(Router router) {
        router.get("/api/users").handler(this::listOrSearch);
        router.get("/api/users/:id").handler(this::getById);
        router.post("/api/users").handler(this::create);
        router.put("/api/users/:id").handler(this::update);
        router.delete("/api/users/:id").handler(this::delete);
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
}
