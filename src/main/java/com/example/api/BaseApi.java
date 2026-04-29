package com.example.api;

import com.example.core.ApiResponse;
import com.example.core.BusinessException;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.List;

/**
 * Base API class — provides shared utilities for all route handlers.
 * Extend this class and implement {@link #registerRoutes(Router)} to add new API modules.
 */
public abstract class BaseApi {

    protected final Vertx vertx;

    protected BaseApi(Vertx vertx) {
        this.vertx = vertx;
    }

    /**
     * Register all routes for this API module.
     * @param router       the router to register routes on
     * @param contextPath  the URL prefix (e.g. "" or "/support"), never null
     */
    public abstract void registerRoutes(Router router, String contextPath);

    // ================================================================
    // Response helpers
    // ================================================================

    protected void ok(RoutingContext ctx, Object data) {
        ctx.json(ApiResponse.success(data).toJson());
    }

    protected void created(RoutingContext ctx, Object data) {
        ctx.response().setStatusCode(201);
        ctx.json(ApiResponse.success(data).toJson());
    }

    protected void noContent(RoutingContext ctx) {
        ctx.response().setStatusCode(204).end();
    }

    // ================================================================
    // Error helpers
    // ================================================================

    protected void fail(RoutingContext ctx, Throwable err) {
        if (err instanceof BusinessException be) {
            ctx.response().setStatusCode(be.getHttpStatus());
            ctx.json(ApiResponse.error(be.getCode(), be.getMessage()).toJson());
        } else {
            ctx.response().setStatusCode(500);
            ctx.json(ApiResponse.error("INTERNAL_ERROR",
                err.getMessage() != null ? err.getMessage() : "Unknown error").toJson());
        }
    }

    protected void badRequest(RoutingContext ctx, String msg) {
        ctx.response().setStatusCode(400);
        ctx.json(ApiResponse.error("BAD_REQUEST", msg).toJson());
    }

    protected void notFound(RoutingContext ctx, String msg) {
        ctx.response().setStatusCode(404);
        ctx.json(ApiResponse.error("NOT_FOUND", msg).toJson());
    }

    // ================================================================
    // Parameter helpers
    // ================================================================

    protected Long parseId(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Long.parseLong(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }

    protected int queryInt(RoutingContext ctx, String param, int fallback) {
        return ctx.queryParam(param).stream()
            .findFirst()
            .map(s -> { try { return Integer.parseInt(s); } catch (Exception e) { return fallback; } })
            .orElse(fallback);
    }

    protected int queryIntClamped(RoutingContext ctx, String param, int fallback, int min, int max) {
        int v = queryInt(ctx, param, fallback);
        return Math.min(max, Math.max(min, v));
    }

    protected String queryStr(RoutingContext ctx, String param) {
        return ctx.queryParam(param).stream().findFirst().orElse(null);
    }

    protected String queryStr(RoutingContext ctx, String param, String fallback) {
        return ctx.queryParam(param).stream().findFirst().orElse(fallback);
    }

    protected JsonObject bodyJson(RoutingContext ctx) {
        return ctx.body().asJsonObject();
    }

    // ================================================================
    // Async chain helpers — convert Handler-based callbacks to Future
    // ================================================================

    protected <T> void respond(RoutingContext ctx, Future<T> future) {
        future.onSuccess(data -> ok(ctx, data))
              .onFailure(err -> fail(ctx, err));
    }

    protected <T> void respondCreated(RoutingContext ctx, Future<T> future) {
        future.onSuccess(data -> created(ctx, data))
              .onFailure(err -> fail(ctx, err));
    }

    protected <T> void respondDeleted(RoutingContext ctx, Future<T> future) {
        future.onSuccess(data -> { ctx.response().setStatusCode(204).end(); })
              .onFailure(err -> fail(ctx, err));
    }

    protected <T> void respondPaginated(RoutingContext ctx, Future<com.example.core.PageResult<T>> future) {
        future.onSuccess(r -> ctx.json(ApiResponse.success(r.toJson()).toJson()))
              .onFailure(err -> fail(ctx, err));
    }
}
