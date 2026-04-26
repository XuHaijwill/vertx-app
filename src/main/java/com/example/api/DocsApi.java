package com.example.api;

import com.example.core.ApiResponse;
import com.example.core.Config;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * API documentation — /docs, /swagger-ui/*, /openapi.yaml, /api/info
 *
 * Swagger UI is served from the swagger-ui webjar (v5.32.4).
 * We inject our own index.html and swagger-initializer.js to override
 * the bundled Petstore default with our real OpenAPI spec.
 */
public class DocsApi extends BaseApi {

    private static final String SWAGGER_UI_VERSION = "5.32.4";

    // Custom index.html — loads our swagger-initializer.js (NOT the webjar's petstore one)
    private static final String INDEX_HTML =
        "<!DOCTYPE html>\n" +
        "<html lang=\"en\">\n" +
        "<head>\n" +
        "  <meta charset=\"UTF-8\">\n" +
        "  <title>Vert.x API - Swagger UI</title>\n" +
        "  <link rel=\"stylesheet\" type=\"text/css\" href=\"./swagger-ui.css\">\n" +
        "  <link rel=\"icon\" type=\"image/png\" href=\"./favicon-32x32.png\" sizes=\"32x32\">\n" +
        "</head>\n" +
        "<body>\n" +
        "  <div id=\"swagger-ui\"></div>\n" +
        "  <script src=\"./swagger-ui-bundle.js\"></script>\n" +
        "  <script src=\"./swagger-ui-standalone-preset.js\"></script>\n" +
        "  <script src=\"./swagger-initializer.js\"></script>\n" +   // our custom one
        "</body>\n" +
        "</html>";

    public DocsApi(Vertx vertx) {
        super(vertx);
    }

    @Override
    public void registerRoutes(Router router) {
        // 1. openapi.yaml — serve from classpath with dynamic server URL
        router.get("/openapi.yaml").handler(this::serveOpenApiYaml);

        // 2. Custom swagger-initializer.js (MUST be before the catch-all route)
        router.get("/swagger-ui/swagger-initializer.js").handler(this::serveSwaggerInitializer);

        // 3. Custom index.html for /swagger-ui/ root (overrides webjar's petstore version)
        router.get("/swagger-ui/index.html").handler(this::serveIndexHtml);

        // 4. Catch-all: serve all other swagger-ui assets from webjar (CSS, JS, images, etc.)
        router.route("/swagger-ui/*").handler(this::serveSwaggerUiAssets);

        // 5. Redirect /docs → swagger-ui with deep-link to our spec
        router.get("/docs").handler(ctx ->
            ctx.response()
                .putHeader("Location", "/swagger-ui/index.html?url=/openapi.yaml")
                .setStatusCode(302).end());

        // 6. API metadata
        router.get("/api/info").handler(this::apiInfo);
    }

    // ---- Handlers ----

    private void serveOpenApiYaml(RoutingContext ctx) {
        var is = Thread.currentThread().getContextClassLoader().getResourceAsStream("openapi.yaml");
        if (is == null) {
            ctx.response().setStatusCode(404).end("openapi.yaml not found");
            return;
        }
        try (is) {
            String yaml = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            // Dynamically inject actual server URL based on incoming request
            String host = ctx.request().getHeader("Host");
            if (host == null || host.isEmpty()) host = "localhost:8888";
            String scheme = ctx.request().isSSL() ? "https" : "http";
            String serverUrl = scheme + "://" + host;
            // Only replace the URL value in the servers section, preserve everything else
            yaml = yaml.replaceAll(
                "(?m)^(\\s+- url:)\\s*.+$",
                "$1 " + serverUrl);
            ctx.response()
                .putHeader("Content-Type", "application/yaml")
                .putHeader("Cache-Control", "no-cache")
                .end(Buffer.buffer(yaml));
        } catch (java.io.IOException e) {
            ctx.response().setStatusCode(500).end("Failed to read openapi.yaml");
        }
    }

    private void serveSwaggerInitializer(RoutingContext ctx) {
        // Point to OUR openapi.yaml, NOT the petstore default
        String js = "window.onload = function() {\n" +
            "  window.ui = SwaggerUIBundle({\n" +
            "    url: \"/openapi.yaml\",\n" +
            "    dom_id: '#swagger-ui',\n" +
            "    deepLinking: true,\n" +
            "    presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],\n" +
            "    layout: \"StandaloneLayout\"\n" +
            "  });\n" +
            "};\n";
        ctx.response()
            .putHeader("Content-Type", "application/javascript; charset=utf-8")
            .putHeader("Cache-Control", "no-store, no-cache, must-revalidate")
            .end(Buffer.buffer(js));
    }

    private void serveIndexHtml(RoutingContext ctx) {
        // Serve our custom index.html that loads our swagger-initializer.js
        ctx.response()
            .putHeader("Content-Type", "text/html; charset=utf-8")
            .putHeader("Cache-Control", "no-store, no-cache, must-revalidate")
            .end(Buffer.buffer(INDEX_HTML));
    }

    private void serveSwaggerUiAssets(RoutingContext ctx) {
        String reqPath = ctx.request().path();
        String resource = reqPath.replaceFirst("^/swagger-ui/", "META-INF/resources/webjars/swagger-ui/" + SWAGGER_UI_VERSION + "/");
        if (resource.endsWith("/")) resource += "index.html";

        var is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        if (is == null) {
            ctx.response().setStatusCode(404).end("Resource not found: " + resource);
            return;
        }
        try (is) {
            byte[] data = is.readAllBytes();
            String ct = reqPath.endsWith(".css") ? "text/css" :
                        reqPath.endsWith(".js")  ? "application/javascript" :
                        reqPath.endsWith(".html") ? "text/html" :
                        reqPath.endsWith(".png")   ? "image/png" :
                        reqPath.endsWith(".svg")   ? "image/svg+xml" : "text/plain";
            ctx.response().putHeader("Content-Type", ct).end(Buffer.buffer(data));
        } catch (java.io.IOException e) {
            ctx.response().setStatusCode(500).end("Failed to read: " + resource);
        }
    }

    private void apiInfo(RoutingContext ctx) {
        var cfg = ctx.vertx().getOrCreateContext().config();
        ok(ctx, new JsonObject()
            .put("name", "Vert.x REST API")
            .put("version", "1.0.0")
            .put("profile", Config.getProfile(cfg))
            .put("java", System.getProperty("java.version"))
            .put("openapi", "/openapi.yaml")
            .put("swagger-ui", "/docs"));
    }
}
