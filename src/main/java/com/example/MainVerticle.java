package com.example;

import com.example.api.*;
import com.example.core.ApiResponse;
import com.example.core.Config;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Main Verticle — HTTP server entry point.
 *
 * Responsibilities:
 * - Deploy DatabaseVerticle
 * - Bootstrap Vert.x Router with global handlers
 * - Register API modules (HealthApi, UserApi, ProductApi, DocsApi)
 * - Start HTTP server
 *
 * All business logic lives in com.example.api.* — add new API modules here.
 */
public class MainVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    private HttpServer server;

    // ================================================================
    // Lifecycle
    // ================================================================

    @Override
    public void start(Promise<Void> startPromise) {
        deployDatabaseVerticle()
            .compose(v -> createRouter())
            .compose(router -> startServer(router))
            .onSuccess(port -> printBanner(port))
            .onSuccess(port -> startPromise.complete())
            .onFailure(err -> {
                LOG.error("[FAIL] Startup failed", err);
                startPromise.fail(err);
            });
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        if (server == null) { stopPromise.complete(); return; }
        server.close()
            .onSuccess(v -> { LOG.info("[OK] Server stopped"); stopPromise.complete(); })
            .onFailure(stopPromise::fail);
    }

    // ================================================================
    // Deploy DatabaseVerticle
    // ================================================================

    private Future<Void> deployDatabaseVerticle() {
        Promise<Void> p = Promise.promise();
        vertx.deployVerticle("com.example.db.DatabaseVerticle",
                new io.vertx.core.DeploymentOptions().setConfig(config()))
            .onSuccess(id -> { LOG.info("[OK] DatabaseVerticle deployed"); p.complete(); })
            .onFailure(err -> { LOG.warn("[WARN] DatabaseVerticle failed — demo mode: {}", err.getMessage()); p.complete(); });
        return p.future();
    }

    // ================================================================
    // Router bootstrap
    // ================================================================

    private Future<Router> createRouter() {
        Router router = Router.router(vertx);

        addGlobalHandlers(router);
        registerApis(router);
        addErrorHandlers(router);

        LOG.info("[OK] Router created");
        return Future.succeededFuture(router);
    }

    /** Register all API modules. Add new API classes here. */
    private void registerApis(Router router) {
        new HealthApi(vertx).registerRoutes(router);
        new UserApi(vertx).registerRoutes(router);
        new ProductApi(vertx).registerRoutes(router);
        new DocsApi(vertx).registerRoutes(router);
        LOG.info("[OK] APIs registered: Health, User, Product, Docs");
    }

    // ================================================================
    // Global handlers (run for every request)
    // ================================================================

    private void addGlobalHandlers(Router router) {
        // Metrics
        router.route().handler(ResponseTimeHandler.create());
        router.route().handler(LoggerHandler.create());

        // CORS
        Set<HttpMethod> methods = new HashSet<>(List.of(
            HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
            HttpMethod.DELETE, HttpMethod.PATCH, HttpMethod.OPTIONS));
        Set<String> headers = new HashSet<>(List.of(
            "x-requested-with", "origin", "Content-Type", "accept",
            "Authorization", "X-Request-ID"));
        router.route().handler(CorsHandler.create()
            .addOrigin("*")
            .allowedHeaders(headers)
            .allowedMethods(methods)
            .maxAgeSeconds(86400));

        // Request body parsing
        router.route().handler(BodyHandler.create());

        // Request ID injection
        router.route().handler(ctx -> {
            String requestId = ctx.request().getHeader("X-Request-ID");
            if (requestId == null) requestId = UUID.randomUUID().toString();
            ctx.put("requestId", requestId);
            ctx.response().putHeader("X-Request-ID", requestId);
            ctx.next();
        });
    }

    // ================================================================
    // Error handlers
    // ================================================================

    private void addErrorHandlers(Router router) {
        router.errorHandler(404, ctx ->
            ctx.json(ApiResponse.error("NOT_FOUND", "Endpoint not found: " + ctx.request().path()).toJson()));
        router.errorHandler(500, ctx -> {
            LOG.error("500 Error", ctx.failure());
            ctx.json(ApiResponse.error("INTERNAL_ERROR", "Internal server error").toJson());
        });
    }

    // ================================================================
    // Server start
    // ================================================================

    private Future<Integer> startServer(Router router) {
        Promise<Integer> p = Promise.promise();
        int port = Config.getHttpPort(config());
        attemptListen(router, port, 0, p);
        return p.future();
    }

    private void attemptListen(Router router, int port, int attempts, Promise<Integer> p) {
        vertx.createHttpServer().requestHandler(router).listen(port)
            .onSuccess(srv -> { server = srv; p.complete(port); })
            .onFailure(err -> {
                boolean bind = err instanceof java.net.BindException ||
                    (err.getMessage() != null && err.getMessage().toLowerCase().contains("address already in use"));
                if (bind && attempts < 10) {
                    LOG.warn("Port {} in use — trying {}", port, port + 1);
                    attemptListen(router, port + 1, attempts + 1, p);
                } else {
                    p.fail(err);
                }
            });
    }

    // ================================================================
    // Banner
    // ================================================================

    private void printBanner(int port) {
        var cfg = config();
        String profile = Config.getProfile(cfg);
        String dbStatus = com.example.db.DatabaseVerticle.getPool(vertx) != null ? "connected" : "demo-mode";
        LOG.info("+============================================================+");
        LOG.info("+            VERT.X APPLICATION STARTED                     +");
        LOG.info("+------------------------------------------------------------+");
        LOG.info("+  HTTP:      http://localhost:{}/                           +", port);
        LOG.info("+  Health:    http://localhost:{}/health                     +", port);
        LOG.info("+  Users:     http://localhost:{}/api/users                  +", port);
        LOG.info("+  Products:  http://localhost:{}/api/products              +", port);
        LOG.info("+  Swagger:   http://localhost:{}/docs                      +", port);
        LOG.info("+------------------------------------------------------------+");
        LOG.info("+  Profile:   {}  |  DB: {}  |  Java: {}        +",
            profile.isEmpty() ? "(default)" : profile,
            dbStatus,
            System.getProperty("java.version"));
        LOG.info("+============================================================+");
    }
}
