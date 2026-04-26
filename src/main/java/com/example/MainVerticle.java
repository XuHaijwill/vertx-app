package com.example;

import com.example.core.ApiResponse;
import com.example.core.BusinessException;
import com.example.core.Config;
import com.example.core.PageResult;
import com.example.core.RequestValidator;
import com.example.db.DatabaseVerticle;
import com.example.repository.ProductRepository;
import com.example.repository.UserRepository;
import com.example.service.ProductService;
import com.example.service.ProductServiceImpl;
import com.example.service.UserService;
import com.example.service.UserServiceImpl;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Main Verticle — HTTP REST API server.
 *
 * Config is injected via DeploymentOptions.setConfig(config) from App.java.
 * All config keys are defined in Config.java (KEY_* constants).
 */
public class MainVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    private HttpServer server;
    private UserService userService;
    private ProductService productService;
    private UserRepository userRepository;
    private ProductRepository productRepository;

    private final long startTime = System.currentTimeMillis();

    @Override
    public void start(Promise<Void> startPromise) {
        try {
            deployDatabaseVerticle()
                .compose(v -> {
                    userService     = new UserServiceImpl(vertx);
                    productService  = new ProductServiceImpl(vertx);
                    userRepository  = new UserRepository(vertx);
                    productRepository = new ProductRepository(vertx);
                    LOG.info("[OK] Services initialized");
                    return Future.succeededFuture();
                })
                .compose(v -> createRouter())
                .compose(router -> startServer(router))
                .onSuccess(port -> {
                    printBanner(port);
                    startPromise.complete();
                })
                .onFailure(err -> {
                    LOG.error("[FAIL] Startup failed", err);
                    startPromise.fail(err);
                });
        } catch (Exception e) {
            LOG.error("[FAIL] Startup exception", e);
            startPromise.fail(e);
        }
    }

    // ================================================================
    // DEPLOY DATABASE VERTICLE
    // ================================================================

    private Future<Void> deployDatabaseVerticle() {
        Promise<Void> p = Promise.promise();
        vertx.deployVerticle("com.example.db.DatabaseVerticle",
            new io.vertx.core.DeploymentOptions().setConfig(config()),
            ar -> {
                if (ar.succeeded()) {
                    LOG.info("[OK] DatabaseVerticle deployed");
                    p.complete();
                } else {
                    LOG.warn("[WARN] DatabaseVerticle failed: {}", ar.cause().getMessage());
                    p.complete();  // demo mode — don't fail startup
                }
            });
        return p.future();
    }

    // ================================================================
    // ROUTER SETUP
    // ================================================================

    private Future<Router> createRouter() {
        Router router = Router.router(vertx);
        addGlobalHandlers(router);
        addHealthRoutes(router);
        addUserRoutes(router);
        addProductRoutes(router);
        addSwaggerRoutes(router);
        addErrorHandlers(router);
        LOG.info("[OK] Router created");
        return Future.succeededFuture(router);
    }

    private void addGlobalHandlers(Router router) {
        router.route().handler(ResponseTimeHandler.create());
        router.route().handler(LoggerHandler.create());

        Set<HttpMethod> methods = new HashSet<>(List.of(
            HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
            HttpMethod.DELETE, HttpMethod.PATCH, HttpMethod.OPTIONS));

        Set<String> headers = new HashSet<>(List.of(
            "x-requested-with", "origin", "Content-Type", "accept",
            "Authorization", "X-Request-ID"));

        router.route().handler(CorsHandler.create("*")
            .allowedHeaders(headers)
            .allowedMethods(methods)
            .maxAgeSeconds(86400));

        router.route().handler(BodyHandler.create());

        router.route().handler(ctx -> {
            String requestId = ctx.request().getHeader("X-Request-ID");
            if (requestId == null) requestId = UUID.randomUUID().toString();
            ctx.put("requestId", requestId);
            ctx.response().putHeader("X-Request-ID", requestId);
            ctx.next();
        });
    }

    // ================================================================
    // HEALTH ROUTES
    // ================================================================

    private void addHealthRoutes(Router router) {
        router.get("/health").handler(ctx -> {
            JsonObject health = new JsonObject()
                .put("status", "UP")
                .put("service", "vertx-app")
                .put("version", "1.0.0")
                .put("profile", Config.getProfile(config()))
                .put("timestamp", System.currentTimeMillis())
                .put("uptime", getUptime())
                .put("memory", getMemoryInfo())
                .put("database", DatabaseVerticle.getPool(vertx) != null ? "connected" : "demo-mode");
            ctx.json(ApiResponse.success(health).toJson());
        });
        router.get("/health/live").handler(ctx -> ctx.response().end("OK"));
        router.get("/health/ready").handler(ctx -> ctx.json(new JsonObject().put("status", "READY")));
    }

    // ================================================================
    // USER ROUTES
    // ================================================================

    private void addUserRoutes(Router router) {
        Router ur = Router.router(vertx);

        ur.get("/").handler(ctx -> {
            String q    = ctx.queryParam("q").stream().findFirst().orElse("");
            int page    = Int(ctx.queryParam("page"), 1);
            int size    = Int(ctx.queryParam("size"), 20);
            size = Math.min(100, Math.max(1, size));
            if (!q.isEmpty()) {
                userService.search(q)
                    .onSuccess(r -> ctx.json(ApiResponse.success(r).toJson()))
                    .onFailure(e -> handleError(ctx, e));
            } else {
                userService.findPaginated(page, size)
                    .onSuccess(r -> ctx.json(ApiResponse.success(r.toJson()).toJson()))
                    .onFailure(e -> handleError(ctx, e));
            }
        });

        ur.get("/:id").handler(ctx -> {
            Long id = parseId(ctx.pathParam("id"));
            if (id == null) { handleError(ctx, BusinessException.badRequest("Invalid user ID")); return; }
            userService.findById(id)
                .onSuccess(r -> ctx.json(ApiResponse.success(r).toJson()))
                .onFailure(e -> handleError(ctx, e));
        });

        ur.post("/").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            RequestValidator.ValidationResult vr = RequestValidator.validateCreateUser(body);
            if (!vr.isValid()) {
                ctx.response().setStatusCode(400);
                ctx.json(ApiResponse.error("VALIDATION_ERROR", vr.getErrors().toString()).toJson());
                return;
            }
            userService.create(body)
                .onSuccess(r -> { ctx.response().setStatusCode(201); ctx.json(ApiResponse.success("User created", r).toJson()); })
                .onFailure(e -> handleError(ctx, e));
        });

        ur.put("/:id").handler(ctx -> {
            Long id = parseId(ctx.pathParam("id"));
            if (id == null) { handleError(ctx, BusinessException.badRequest("Invalid user ID")); return; }
            userService.update(id, ctx.body().asJsonObject())
                .onSuccess(r -> ctx.json(ApiResponse.success("User updated", r).toJson()))
                .onFailure(e -> handleError(ctx, e));
        });

        ur.delete("/:id").handler(ctx -> {
            Long id = parseId(ctx.pathParam("id"));
            if (id == null) { handleError(ctx, BusinessException.badRequest("Invalid user ID")); return; }
            userService.delete(id)
                .onSuccess(r -> ctx.json(ApiResponse.success("User deleted", null).toJson()))
                .onFailure(e -> handleError(ctx, e));
        });

        router.mountSubRouter("/api/users", ur);
    }

    // ================================================================
    // PRODUCT ROUTES
    // ================================================================

    private void addProductRoutes(Router router) {
        Router pr = Router.router(vertx);

        pr.get("/").handler(ctx -> {
            String q = ctx.queryParam("q").stream().findFirst().orElse("");
            String cat = ctx.queryParam("category").stream().findFirst().orElse(null);
            if (!q.isEmpty() || cat != null) {
                productService.search(q, cat)
                    .onSuccess(r -> ctx.json(ApiResponse.success(r).toJson()))
                    .onFailure(e -> handleError(ctx, e));
            } else {
                productService.findAll()
                    .onSuccess(r -> ctx.json(ApiResponse.success(r).toJson()))
                    .onFailure(e -> handleError(ctx, e));
            }
        });

        pr.get("/:id").handler(ctx -> {
            Long id = parseId(ctx.pathParam("id"));
            if (id == null) { handleError(ctx, BusinessException.badRequest("Invalid product ID")); return; }
            productService.findById(id)
                .onSuccess(r -> ctx.json(ApiResponse.success(r).toJson()))
                .onFailure(e -> handleError(ctx, e));
        });

        pr.post("/").handler(ctx -> {
            productService.create(ctx.body().asJsonObject())
                .onSuccess(r -> { ctx.response().setStatusCode(201); ctx.json(ApiResponse.success("Product created", r).toJson()); })
                .onFailure(e -> handleError(ctx, e));
        });

        pr.put("/:id").handler(ctx -> {
            Long id = parseId(ctx.pathParam("id"));
            if (id == null) { handleError(ctx, BusinessException.badRequest("Invalid product ID")); return; }
            productService.update(id, ctx.body().asJsonObject())
                .onSuccess(r -> ctx.json(ApiResponse.success("Product updated", r).toJson()))
                .onFailure(e -> handleError(ctx, e));
        });

        pr.delete("/:id").handler(ctx -> {
            Long id = parseId(ctx.pathParam("id"));
            if (id == null) { handleError(ctx, BusinessException.badRequest("Invalid product ID")); return; }
            productService.delete(id)
                .onSuccess(r -> ctx.json(ApiResponse.success("Product deleted", null).toJson()))
                .onFailure(e -> handleError(ctx, e));
        });

        router.mountSubRouter("/api/products", pr);
    }

    // ================================================================
    // SWAGGER / API DOCS
    // ================================================================

    private void addSwaggerRoutes(Router router) {
        // Serve openapi.yaml from classpath
        router.get("/openapi.yaml").handler(ctx -> {
            var is = Thread.currentThread().getContextClassLoader().getResourceAsStream("openapi.yaml");
            if (is == null) { ctx.response().setStatusCode(404).end("openapi.yaml not found"); return; }
            try (is) {
                byte[] data = is.readAllBytes();
                ctx.response().putHeader("Content-Type", "application/yaml").end(Buffer.buffer(data));
            } catch (java.io.IOException e) {
                ctx.response().setStatusCode(500).end("Failed to read openapi.yaml");
            }
        });

        // Swagger UI from webjar
        router.route("/swagger-ui/*").handler(StaticHandler.create()
            .setWebRoot("META-INF/resources/webjars/swagger-ui/5.20.7"));

        router.get("/docs").handler(ctx ->
            ctx.response()
                .putHeader("Location", "/swagger-ui/index.html?url=/openapi.yaml")
                .setStatusCode(302).end());

        router.get("/api/info").handler(ctx ->
            ctx.json(ApiResponse.success(new JsonObject()
                .put("name", "Vert.x REST API")
                .put("version", "1.0.0")
                .put("profile", Config.getProfile(config()))
                .put("java", System.getProperty("java.version"))
                .put("openapi", "/openapi.yaml")
                .put("swagger-ui", "/docs"))));
    }

    // ================================================================
    // ERROR HANDLERS
    // ================================================================

    private void addErrorHandlers(Router router) {
        router.errorHandler(404, ctx ->
            ctx.json(ApiResponse.error("NOT_FOUND", "Endpoint not found: " + ctx.request().path())));
        router.errorHandler(500, ctx -> {
            LOG.error("500 Error", ctx.failure());
            ctx.json(ApiResponse.error("INTERNAL_ERROR", "Internal server error").toJson());
        });
    }

    private void handleError(RoutingContext ctx, Throwable err) {
        if (err instanceof BusinessException be) {
            ctx.response().setStatusCode(be.getHttpStatus());
            ctx.json(ApiResponse.error(be.getCode(), be.getMessage()).toJson());
        } else {
            LOG.error("Handler error", err);
            ctx.response().setStatusCode(500);
            ctx.json(ApiResponse.error("INTERNAL_ERROR", err.getMessage()).toJson());
        }
    }

    // ================================================================
    // SERVER STARTUP
    // ================================================================

    private Future<Integer> startServer(Router router) {
        Promise<Integer> p = Promise.promise();
        int port = Config.getHttpPort(config());
        attemptListen(router, port, 0, p);
        return p.future();
    }

    private void attemptListen(Router router, int port, int attempts, Promise<Integer> p) {
        final int MAX = 10;
        vertx.createHttpServer().requestHandler(router).listen(port)
            .onSuccess(srv -> { server = srv; p.complete(port); })
            .onFailure(err -> {
                boolean bind = err instanceof java.net.BindException ||
                    (err.getMessage() != null && err.getMessage().toLowerCase().contains("address already in use"));
                if (bind && attempts < MAX) {
                    LOG.warn("Port {} in use — trying {}", port, port + 1);
                    attemptListen(router, port + 1, attempts + 1, p);
                } else {
                    p.fail(err);
                }
            });
    }

    // ================================================================
    // UTILITIES
    // ================================================================

    private Long parseId(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return null; }
    }

    private int Int(List<String> list, int fallback) {
        return list.stream().findFirst().map(s -> {
            try { return Integer.parseInt(s); } catch (Exception e) { return fallback; }
        }).orElse(fallback);
    }

    private String getUptime() {
        long s = (System.currentTimeMillis() - startTime) / 1000;
        long m = s / 60, h = m / 60, d = h / 24;
        if (d > 0) return d + "d " + (h % 24) + "h";
        if (h > 0) return h + "h " + (m % 60) + "m";
        if (m > 0) return m + "m " + (s % 60) + "s";
        return s + "s";
    }

    private JsonObject getMemoryInfo() {
        Runtime rt = Runtime.getRuntime();
        long total = rt.totalMemory(), free = rt.freeMemory(), used = total - free;
        return new JsonObject()
            .put("total", (total / 1024 / 1024) + "MB")
            .put("used",  (used  / 1024 / 1024) + "MB")
            .put("free",  (free  / 1024 / 1024) + "MB")
            .put("pct",   String.format("%.1f%%", (double) used / total * 100));
    }

    private void printBanner(int port) {
        String p = Config.getProfile(config());
        String dbStatus = DatabaseVerticle.getPool(vertx) != null ? "connected" : "demo-mode";
        LOG.info("+============================================================+");
        LOG.info("+            VERT.X APPLICATION STARTED                     +");
        LOG.info("+------------------------------------------------------------+");
        LOG.info("+  HTTP:      http://localhost:{}/                           +", port);
        LOG.info("+  Health:    http://localhost:{}/health                     +", port);
        LOG.info("+  Users API: http://localhost:{}/api/users                  +", port);
        LOG.info("+  Products:  http://localhost:{}/api/products              +", port);
        LOG.info("+  Swagger:   http://localhost:{}/docs                      +", port);
        LOG.info("+------------------------------------------------------------+");
        LOG.info("+  Profile:   {}  |  DB: {}  |  Java: {}        +",
            p.isEmpty() ? "(default)" : p, dbStatus, System.getProperty("java.version"));
        LOG.info("+============================================================+");
    }

    @Override
    public void stop(Promise<Void> p) {
        if (server != null) {
            server.close()
                .onSuccess(v -> { LOG.info("[OK] Server stopped"); p.complete(); })
                .onFailure(p::fail);
        } else {
            p.complete();
        }
    }
}
