package com.example.handlers;

import com.example.core.ApiResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized error handler.
 * <p>
 * All methods write {@link ApiResponse}-wrapped JSON, ensuring consistent
 * response format across the entire application.
 * <p>
 * Usage from Vert.x router:
 * <pre>
 * router.errorHandler(404, ErrorHandler::notFound);
 * router.errorHandler(500, ErrorHandler::internalError);
 * </pre>
 * Or for programmatic use:
 * <pre>
 * ErrorHandler.unauthorized(ctx);
 * ErrorHandler.forbidden(ctx);
 * </pre>
 */
public class ErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ErrorHandler.class);

    /** Mask internal details from client-facing messages in production. */
    private static final boolean SAFE_MESSAGES = !isDevMode();

    private static boolean isDevMode() {
        String profile = System.getProperty("vertx.profile", "");
        return "dev".equalsIgnoreCase(profile) || "development".equalsIgnoreCase(profile);
    }

    /**
     * Handle 404 Not Found.
     * Called by Vert.x when no route matches.
     */
    public static void notFound(RoutingContext ctx) {
        LOG.warn("[404] {} — {}", ctx.request().method(), ctx.request().path());
        ctx.response()
            .setStatusCode(404)
            .putHeader("Content-Type", "application/json")
            .end(new ApiResponse()
                .setCode("error")
                .setMessage("NOT_FOUND")
                .putExtra("detail", "Endpoint not found: " + ctx.request().path())
                .toJson().encode());
    }

    /**
     * Handle 500 Internal Server Error.
     * Logs the full stack trace server-side; returns a safe message to the client.
     */
    public static void internalError(RoutingContext ctx) {
        Throwable failure = ctx.failure();
        LOG.error("[500] {} — {}", ctx.request().method(), ctx.request().path(), failure);

        String clientMessage = SAFE_MESSAGES
            ? "Internal server error"
            : (failure.getMessage() != null ? failure.getMessage() : "Unknown error");

        ctx.response()
            .setStatusCode(500)
            .putHeader("Content-Type", "application/json")
            .end(new ApiResponse()
                .setCode("error")
                .setMessage("INTERNAL_ERROR")
                .putExtra("detail", clientMessage)
                .toJson().encode());
    }

    /**
     * Handle 400 Bad Request.
     */
    public static void badRequest(RoutingContext ctx, String message) {
        LOG.warn("[400] {} — {}", ctx.request().method(), message);
        ctx.response()
            .setStatusCode(400)
            .putHeader("Content-Type", "application/json")
            .end(new ApiResponse()
                .setCode("error")
                .setMessage("BAD_REQUEST")
                .putExtra("detail", message)
                .toJson().encode());
    }

    /**
     * Handle 401 Unauthorized.
     */
    public static void unauthorized(RoutingContext ctx) {
        LOG.warn("[401] {} — {}", ctx.request().method(), ctx.request().path());
        ctx.response()
            .setStatusCode(401)
            .putHeader("Content-Type", "application/json")
            .end(new ApiResponse()
                .setCode("error")
                .setMessage("UNAUTHORIZED")
                .putExtra("detail", "Authentication required")
                .toJson().encode());
    }

    /**
     * Handle 403 Forbidden.
     */
    public static void forbidden(RoutingContext ctx) {
        LOG.warn("[403] {} — {}", ctx.request().method(), ctx.request().path());
        ctx.response()
            .setStatusCode(403)
            .putHeader("Content-Type", "application/json")
            .end(new ApiResponse()
                .setCode("error")
                .setMessage("FORBIDDEN")
                .putExtra("detail", "Access denied")
                .toJson().encode());
    }
}