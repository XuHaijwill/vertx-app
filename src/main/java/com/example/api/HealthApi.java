package com.example.api;

import com.example.core.ApiResponse;
import com.example.core.Config;
import com.example.db.DatabaseVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * Health check endpoints — /health, /health/live, /health/ready
 */
public class HealthApi extends BaseApi {

    private final long startTime = System.currentTimeMillis();

    public HealthApi(Vertx vertx) {
        super(vertx);
    }

    @Override
    public void registerRoutes(Router router) {
        router.get("/health").handler(this::health);
        router.get("/health/live").handler(this::liveness);
        router.get("/health/ready").handler(this::readiness);
    }

    private void health(RoutingContext ctx) {
        JsonObject health = new JsonObject()
            .put("status", "UP")
            .put("service", "vertx-app")
            .put("version", "1.0.0")
            .put("profile", Config.getProfile(ctx.vertx().getOrCreateContext().config()))
            .put("timestamp", System.currentTimeMillis())
            .put("uptime", getUptime())
            .put("memory", getMemoryInfo())
            .put("database", DatabaseVerticle.getPool(vertx) != null ? "connected" : "demo-mode")
            .put("auth", getAuthInfo(ctx));
        ok(ctx, health);
    }

    private JsonObject getAuthInfo(RoutingContext ctx) {
        var cfg = ctx.vertx().getOrCreateContext().config();
        boolean authEnabled = Config.isAuthEnabled(cfg);
        JsonObject authInfo = new JsonObject()
            .put("enabled", authEnabled);
        if (authEnabled) {
            authInfo
                .put("realm", Config.getAuthRealm(cfg))
                .put("clientId", Config.getAuthClientId(cfg))
                .put("issuer", Config.getAuthIssuer(cfg));
        }
        return authInfo;
    }

    private void liveness(RoutingContext ctx) {
        ctx.response().end("OK");
    }

    private void readiness(RoutingContext ctx) {
        ok(ctx, new JsonObject().put("status", "READY"));
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
}
