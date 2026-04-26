package com.example;

import com.example.core.ApiResponse;
import com.example.core.BusinessException;
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
import java.util.stream.Collectors;

/**
 * Main Verticle - HTTP Server with PostgreSQL & Swagger
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
            // Initialize database verticle
            deployDatabaseVerticle()
                .compose(v -> {
                    // Initialize services
                    userService = new UserServiceImpl(vertx);
                    productService = new ProductServiceImpl(vertx);
                    userRepository = new UserRepository(vertx);
                    productRepository = new ProductRepository(vertx);
                    LOG.info("✅ Services initialized");
                    return Future.succeededFuture();
                })
                .compose(v -> createRouter())
                .compose(router -> startServer(router))
                .onSuccess(port -> {
                    printBanner(port);
                    startPromise.complete();
                })
                .onFailure(err -> {
                    LOG.error("❌ Failed to start", err);
                    startPromise.fail(err);
                });

        } catch (Exception e) {
            LOG.error("❌ Failed to start verticle", e);
            startPromise.fail(e);
        }
    }

    private Future<Void> deployDatabaseVerticle() {
        Promise<Void> promise = Promise.promise();
        vertx.deployVerticle("com.example.db.DatabaseVerticle",
            new io.vertx.core.DeploymentOptions().setConfig(config()),
            ar -> {
                if (ar.succeeded()) {
                    LOG.info("✅ DatabaseVerticle deployed");
                    promise.complete();
                } else {
                    LOG.warn("⚠️  DatabaseVerticle failed: {}", ar.cause().getMessage());
                    // Don't fail - allow demo mode
                    promise.complete();
                }
            });
        return promise.future();
    }

    private Future<Router> createRouter() {
        Promise<Router> promise = Promise.promise();

        // Create router manually; OpenAPI spec is still served as static file
        Router router = createManualRouter();
        LOG.info("✅ Router created (manual mode)");
        promise.complete(router);

        return promise.future();
    }

    private Router createManualRouter() {
        Router router = Router.router(vertx);
        addGlobalHandlers(router);
        addHealthRoutes(router);
        addUserRoutes(router);
        addProductRoutes(router);
        addSwaggerRoutes(router);
        addErrorHandlers(router);
        return router;
    }

    private void addCustomHandlers(Router router) {
        // Add global handlers before OpenAPI handlers
        router.route().handler(ResponseTimeHandler.create());
        router.route().handler(LoggerHandler.create());
        router.route().handler(BodyHandler.create());
        
        // Add CORS
        Set<String> allowedHeaders = new HashSet<>();
        allowedHeaders.addAll(List.of(
            "x-requested-with", "Access-Control-Allow-Origin", "origin",
            "Content-Type", "accept", "Authorization", "X-Request-ID"
        ));
        
        Set<HttpMethod> allowedMethods = new HashSet<>();
        allowedMethods.addAll(List.of(
            HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
            HttpMethod.DELETE, HttpMethod.PATCH, HttpMethod.OPTIONS
        ));
        
        router.route().handler(CorsHandler.create("*")
            .allowedHeaders(allowedHeaders)
            .allowedMethods(allowedMethods)
            .allowCredentials(true));

        // Request ID
        router.route().handler(ctx -> {
            String requestId = ctx.request().getHeader("X-Request-ID");
            ctx.put("requestId", requestId != null ? requestId : UUID.randomUUID().toString());
            ctx.response().putHeader("X-Request-ID", ctx.get("requestId"));
            ctx.next();
        });

        // Swagger UI - serve from classpath webjars
        router.route("/swagger-ui/*").handler(StaticHandler.create()
            .setWebRoot("META-INF/resources/webjars/swagger-ui/5.20.7"));

        // OpenAPI spec endpoint — read from classpath
        router.route("/openapi.yaml").handler(ctx -> {
            java.io.InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("openapi.yaml");
            if (is == null) {
                ctx.response().setStatusCode(404).end("openapi.yaml not found");
                return;
            }
            try (is) {
                byte[] data = is.readAllBytes();
                ctx.response().putHeader("Content-Type", "application/yaml").end(data);
            } catch (java.io.IOException e) {
                ctx.response().setStatusCode(500).end("Failed to read openapi.yaml");
            }
        });

        // Redirect /docs to Swagger UI
        router.get("/docs").handler(ctx ->
            ctx.response().putHeader("Location", "/swagger-ui/index.html?url=/openapi.yaml")
                .setStatusCode(302).end());

        addErrorHandlers(router);
    }

    // ================================================================
    // GLOBAL HANDLERS
    // ================================================================

    private void addGlobalHandlers(Router router) {
        router.route().handler(ResponseTimeHandler.create());
        router.route().handler(LoggerHandler.create());
        
        Set<String> allowedHeaders = new HashSet<>();
        allowedHeaders.addAll(List.of(
            "x-requested-with", "Access-Control-Allow-Origin", "origin",
            "Content-Type", "accept", "Authorization", "X-Request-ID"
        ));
        
        Set<HttpMethod> allowedMethods = new HashSet<>();
        allowedMethods.addAll(List.of(
            HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
            HttpMethod.DELETE, HttpMethod.PATCH, HttpMethod.OPTIONS
        ));
        
        router.route().handler(CorsHandler.create("*")
            .allowedHeaders(allowedHeaders)
            .allowedMethods(allowedMethods)
            .allowCredentials(true)
            .maxAgeSeconds(86400));

        router.route().handler(BodyHandler.create());

        router.route().handler(ctx -> {
            String requestId = ctx.request().getHeader("X-Request-ID");
            ctx.put("requestId", requestId != null ? requestId : UUID.randomUUID().toString());
            ctx.response().putHeader("X-Request-ID", ctx.get("requestId"));
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
                .put("service", "my-vertx-app")
                .put("version", "1.0.0")
                .put("timestamp", System.currentTimeMillis())
                .put("uptime", getUptime())
                .put("memory", getMemoryInfo())
                .put("database", DatabaseVerticle.getPool(vertx) != null ? "connected" : "demo-mode");

            ctx.json(ApiResponse.success(health));
        });

        router.get("/health/live").handler(ctx -> ctx.response().end("OK"));
        router.get("/health/ready").handler(ctx -> ctx.json(new JsonObject().put("status", "READY")));
    }

    // ================================================================
    // USER ROUTES
    // ================================================================

    private void addUserRoutes(Router router) {
        Router userRouter = Router.router(vertx);

        // List/Search users
        userRouter.get("/").handler(ctx -> {
            String q = ctx.queryParam("q").stream().findFirst().orElse("");
            int page = Integer.parseInt(ctx.queryParam("page").stream().findFirst().orElse("1"));
            int size = Integer.parseInt(ctx.queryParam("size").stream().findFirst().orElse("20"));
            size = Math.min(100, Math.max(1, size));

            if (!q.isEmpty()) {
                userService.search(q)
                    .onSuccess(users -> ctx.json(ApiResponse.success(users)))
                    .onFailure(err -> handleError(ctx, err));
            } else {
                userService.findPaginated(page, size)
                    .onSuccess(result -> {
                        ctx.json(ApiResponse.success(result.toJson()));
                    })
                    .onFailure(err -> handleError(ctx, err));
            }
        });

        // Get by ID
        userRouter.get("/:id").handler(ctx -> {
            Long id = parseId(ctx.pathParam("id"));
            if (id == null) {
                handleError(ctx, BusinessException.badRequest("Invalid user ID"));
                return;
            }
            userService.findById(id)
                .onSuccess(user -> ctx.json(ApiResponse.success(user)))
                .onFailure(err -> handleError(ctx, err));
        });

        // Create
        userRouter.post("/").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            RequestValidator.ValidationResult validation = RequestValidator.validateCreateUser(body);
            if (!validation.isValid()) {
                ctx.response().setStatusCode(400)
                    .json(ApiResponse.error("VALIDATION_ERROR", validation.getErrors().toString()));
                return;
            }
            userService.create(body)
                .onSuccess(user -> ctx.response().setStatusCode(201)
                    .json(ApiResponse.success("User created", user)))
                .onFailure(err -> handleError(ctx, err));
        });

        // Update
        userRouter.put("/:id").handler(ctx -> {
            Long id = parseId(ctx.pathParam("id"));
            if (id == null) {
                handleError(ctx, BusinessException.badRequest("Invalid user ID"));
                return;
            }
            userService.update(id, ctx.body().asJsonObject())
                .onSuccess(user -> ctx.json(ApiResponse.success("User updated", user)))
                .onFailure(err -> handleError(ctx, err));
        });

        // Delete
        userRouter.delete("/:id").handler(ctx -> {
            Long id = parseId(ctx.pathParam("id"));
            if (id == null) {
                handleError(ctx, BusinessException.badRequest("Invalid user ID"));
                return;
            }
            userService.delete(id)
                .onSuccess(v -> ctx.json(ApiResponse.success("User deleted", null)))
                .onFailure(err -> handleError(ctx, err));
        });

        router.mountSubRouter("/api/users", userRouter);
    }

    // ================================================================
    // PRODUCT ROUTES
    // ================================================================

    private void addProductRoutes(Router router) {
        Router productRouter = Router.router(vertx);

        // List/Search products
        productRouter.get("/").handler(ctx -> {
            String q = ctx.queryParam("q").stream().findFirst().orElse("");
            String category = ctx.queryParam("category").stream().findFirst().orElse(null);

            if (!q.isEmpty() || category != null) {
                productService.search(q, category)
                    .onSuccess(products -> ctx.json(ApiResponse.success(products)))
                    .onFailure(err -> handleError(ctx, err));
            } else {
                productService.findAll()
                    .onSuccess(products -> ctx.json(ApiResponse.success(products)))
                    .onFailure(err -> handleError(ctx, err));
            }
        });

        // Get by ID
        productRouter.get("/:id").handler(ctx -> {
            Long id = parseId(ctx.pathParam("id"));
            if (id == null) {
                handleError(ctx, BusinessException.badRequest("Invalid product ID"));
                return;
            }
            productService.findById(id)
                .onSuccess(product -> ctx.json(ApiResponse.success(product)))
                .onFailure(err -> handleError(ctx, err));
        });

        // Create
        productRouter.post("/").handler(ctx -> {
            productService.create(ctx.body().asJsonObject())
                .onSuccess(product -> ctx.response().setStatusCode(201)
                    .json(ApiResponse.success("Product created", product)))
                .onFailure(err -> handleError(ctx, err));
        });

        // Update
        productRouter.put("/:id").handler(ctx -> {
            Long id = parseId(ctx.pathParam("id"));
            if (id == null) {
                handleError(ctx, BusinessException.badRequest("Invalid product ID"));
                return;
            }
            productService.update(id, ctx.body().asJsonObject())
                .onSuccess(product -> ctx.json(ApiResponse.success("Product updated", product)))
                .onFailure(err -> handleError(ctx, err));
        });

        // Delete
        productRouter.delete("/:id").handler(ctx -> {
            Long id = parseId(ctx.pathParam("id"));
            if (id == null) {
                handleError(ctx, BusinessException.badRequest("Invalid product ID"));
                return;
            }
            productService.delete(id)
                .onSuccess(v -> ctx.json(ApiResponse.success("Product deleted", null)))
                .onFailure(err -> handleError(ctx, err));
        });

        router.mountSubRouter("/api/products", productRouter);
    }

    // ================================================================
    // SWAGGER ROUTES
    // ================================================================

    private void addSwaggerRoutes(Router router) {
        // OpenAPI spec file — read from classpath
        router.get("/openapi.yaml").handler(ctx -> {
            java.io.InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("openapi.yaml");
            if (is == null) {
                ctx.response().setStatusCode(404).end("openapi.yaml not found");
                return;
            }
            try (is) {
                byte[] data = is.readAllBytes();
                ctx.response().putHeader("Content-Type", "application/yaml").end(data);
            } catch (java.io.IOException e) {
                ctx.response().setStatusCode(500).end("Failed to read openapi.yaml");
            }
        });

        // Swagger UI - serve from classpath webjars
        router.route("/swagger-ui/*").handler(StaticHandler.create()
            .setWebRoot("META-INF/resources/webjars/swagger-ui/5.20.7"));

        // Redirect /docs to Swagger UI
        router.get("/docs").handler(ctx ->
            ctx.response().putHeader("Location", "/swagger-ui/index.html?url=/openapi.yaml")
                .setStatusCode(302)
                .end());

        // API info endpoint
        router.get("/api/info").handler(ctx ->
            ctx.json(ApiResponse.success(new JsonObject()
                .put("name", "My Vert.x REST API")
                .put("version", "1.0.0")
                .put("description", "Enterprise-grade REST API with PostgreSQL")
                .put("java", System.getProperty("java.version"))
                .put("openapi", "/openapi.yaml")
                .put("swagger-ui", "/docs")
                .put("timestamp", System.currentTimeMillis())
            )));
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
            ctx.response().setStatusCode(be.getHttpStatus())
                .json(ApiResponse.error(be.getCode(), be.getMessage()).toJson());
        } else {
            LOG.error("Error", err);
            ctx.response().setStatusCode(500)
                .json(ApiResponse.error("INTERNAL_ERROR", err.getMessage()).toJson());
        }
    }

    // ================================================================
    // SERVER STARTUP
    // ================================================================

    private Future<Integer> startServer(Router router) {
        Promise<Integer> promise = Promise.promise();
        int port = config().getInteger("http.port", 8888);

        vertx.createHttpServer().requestHandler(router).listen(port)
            .onSuccess(srv -> { server = srv; promise.complete(port); })
            .onFailure(promise::fail);

        return promise.future();
    }

    // ================================================================
    // UTILITIES
    // ================================================================

    private Long parseId(String idStr) {
        try { return Long.parseLong(idStr); } catch (Exception e) { return null; }
    }

    private String getUptime() {
        long s = (System.currentTimeMillis() - startTime) / 1000;
        long m = s / 60, h = m / 60, d = h / 24;
        if (d > 0) return String.format("%dd %dh", d, h % 24);
        if (h > 0) return String.format("%dh %dm", h, m % 60);
        if (m > 0) return String.format("%dm %ds", m, s % 60);
        return s + "s";
    }

    private JsonObject getMemoryInfo() {
        Runtime rt = Runtime.getRuntime();
        long total = rt.totalMemory(), free = rt.freeMemory(), used = total - free;
        return new JsonObject()
            .put("total", total / 1024 / 1024 + "MB")
            .put("used", used / 1024 / 1024 + "MB")
            .put("free", free / 1024 / 1024 + "MB")
            .put("percentage", String.format("%.1f%%", (double) used / total * 100));
    }

    private void printBanner(int port) {
        LOG.info("");
        LOG.info("╔══════════════════════════════════════════════════════╗");
        LOG.info("║           ✅ VERT.X APPLICATION STARTED              ║");
        LOG.info("╠══════════════════════════════════════════════════════╣");
        LOG.info("║  🌐 HTTP:     http://localhost:{}/                    ║", String.format("%-6s", port));
        LOG.info("║  📊 Health:   http://localhost:{}/health               ║", port);
        LOG.info("║  👥 Users:    http://localhost:{}/api/users            ║", port);
        LOG.info("║  📦 Products: http://localhost:{}/api/products        ║", port);
        LOG.info("╠══════════════════════════════════════════════════════╣");
        LOG.info("║  📖 API Docs: http://localhost:{}/docs                 ║", port);
        LOG.info("║  📄 OpenAPI: http://localhost:{}/openapi.yaml         ║", port);
        LOG.info("╚══════════════════════════════════════════════════════╝");
        LOG.info("");
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        if (server != null) {
            server.close().onSuccess(v -> { LOG.info("🛑 Server stopped"); stopPromise.complete(); })
                .onFailure(stopPromise::fail);
        } else {
            stopPromise.complete();
        }
    }
}
