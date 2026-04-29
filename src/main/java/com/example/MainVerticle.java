package com.example;

import com.example.api.*;
import com.example.auth.AuthConfig;
import com.example.auth.KeycloakAuthHandler;
import com.example.core.ApiResponse;
import com.example.core.Config;
import com.example.verticles.SchedulerVerticle;
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
            .compose(v -> deploySchedulerVerticle())
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

    private Future<Void> deploySchedulerVerticle() {
        if (!Config.isSchedulerEnabled(config())) {
            LOG.info("[SCHEDULER] Disabled via config");
            return Future.succeededFuture();
        }
        Promise<Void> p = Promise.promise();
        vertx.deployVerticle("com.example.verticles.SchedulerVerticle",
                new io.vertx.core.DeploymentOptions().setConfig(config()))
            .onSuccess(id -> { LOG.info("[OK] SchedulerVerticle deployed"); p.complete(); })
            .onFailure(err -> { LOG.warn("[WARN] SchedulerVerticle failed — continuing: {}", err.getMessage()); p.complete(); });
        return p.future();
    }

    // ================================================================
    // Router bootstrap
    // ================================================================

    private Future<Router> createRouter() {
        Router router = Router.router(vertx);
        String contextPath = Config.getContextPath(config());

        addGlobalHandlers(router);

        // Setup Keycloak auth if enabled
        return setupAuth(router, contextPath)
            .map(authHandler -> {
                registerApis(router, authHandler, contextPath);
                addErrorHandlers(router);
                LOG.info("[OK] Router created with context-path: '{}'", contextPath);
                return router;
            });
    }

    /**
     * Setup Keycloak JWT authentication handler if auth is enabled.
     * Returns null handler if auth is disabled.
     */
    private Future<KeycloakAuthHandler> setupAuth(Router router, String contextPath) {
        AuthConfig authConfig = AuthConfig.from(config());

        if (!authConfig.isEnabled()) {
            LOG.info("[AUTH] Authentication disabled — all endpoints are open");
            return Future.succeededFuture(null);
        }

        LOG.info("[AUTH] Authentication enabled — issuer={}, clientId={}",
            authConfig.getIssuer(), authConfig.getClientId());

        // Paths that skip authentication (加上 context-path 前缀)
        Set<String> skipPaths = new HashSet<>(java.util.Arrays.asList(
            contextPath + "/health",
            contextPath + "/health/",
            contextPath + "/docs",
            contextPath + "/swagger-ui/",
            contextPath + "/openapi.yaml",
            contextPath + "/api/auth/config",
            contextPath + "/api/info",
            // User API 白名单（方便测试）
            contextPath + "/api/users",
            contextPath + "/api/users/"
        ));

        return KeycloakAuthHandler.create(vertx, authConfig, skipPaths)
            .onSuccess(handler -> {
                // Apply auth handler to /api/* routes under context-path
                router.route(contextPath + "/api/*").handler(handler);
                LOG.info("[AUTH] Keycloak auth handler installed on {}/api/*", contextPath);
            })
            .onFailure(err -> {
                LOG.error("[AUTH] Failed to setup Keycloak auth: {}", err.getMessage());
                LOG.warn("[AUTH] Continuing without authentication");
            })
            .recover(err -> Future.succeededFuture(null));
    }

    /** Register all API modules. Add new API classes here. */
    private void registerApis(Router router, KeycloakAuthHandler authHandler, String contextPath) {
        AuthConfig authConfig = AuthConfig.from(config());

        new HealthApi(vertx).registerRoutes(router, contextPath);
        new UserApi(vertx).registerRoutes(router, contextPath);
        new ProductApi(vertx).registerRoutes(router, contextPath);
        new DocsApi(vertx).registerRoutes(router, contextPath);
        new AuthApi(vertx, authConfig).registerRoutes(router, contextPath);

        LOG.info("[OK] APIs registered: Health, User, Product, Docs, Auth");
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
        String contextPath = Config.getContextPath(cfg);
        String baseUrl = "http://localhost:" + port + contextPath;

        LOG.info("+============================================================+");
        LOG.info("+            VERT.X APPLICATION STARTED                     +");
        LOG.info("+------------------------------------------------------------+");
        LOG.info("+  HTTP:      {}   +", padRight(baseUrl + "/", 44));
        LOG.info("+  Health:    {}/health                     +", baseUrl);
        LOG.info("+  Users:     {}/api/users                  +", baseUrl);
        LOG.info("+  Products:  {}/api/products              +", baseUrl);
        LOG.info("+  Swagger:   {}/docs                      +", baseUrl);
        LOG.info("+------------------------------------------------------------+");
        LOG.info("+  Profile:   {}  |  DB: {}  |  Java: {}        +",
            padRight(profile.isEmpty() ? "(default)" : profile, 10), dbStatus,
            System.getProperty("java.version"));
        LOG.info("+  Context:   {}                                 +", contextPath.isEmpty() ? "(none)" : contextPath);
        LOG.info("+============================================================+");
    }

    private static String padRight(String s, int len) {
        if (s.length() >= len) return s;
        return s + " ".repeat(len - s.length());
    }
}
