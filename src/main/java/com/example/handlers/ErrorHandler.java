package com.example.handlers;

import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Error Handler - Centralized error handling
 */
public class ErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ErrorHandler.class);

    /**
     * Handle 404 Not Found
     */
    public static void notFound(RoutingContext ctx) {
        LOG.warn("404 Not Found: {}", ctx.request().path());
        ctx.response()
            .setStatusCode(404)
            .putHeader("Content-Type", "application/json")
            .end("{\"error\":\"Not Found\",\"path\":\"" + ctx.request().path() + "\"}");
    }

    /**
     * Handle 500 Internal Server Error
     */
    public static void internalError(RoutingContext ctx) {
        LOG.error("500 Internal Error: {}", ctx.failure().getMessage(), ctx.failure());
        ctx.response()
            .setStatusCode(500)
            .putHeader("Content-Type", "application/json")
            .end("{\"error\":\"Internal Server Error\",\"message\":\"" + ctx.failure().getMessage() + "\"}");
    }

    /**
     * Handle 400 Bad Request
     */
    public static void badRequest(RoutingContext ctx, String message) {
        LOG.warn("400 Bad Request: {}", message);
        ctx.response()
            .setStatusCode(400)
            .putHeader("Content-Type", "application/json")
            .end("{\"error\":\"Bad Request\",\"message\":\"" + message + "\"}");
    }

    /**
     * Handle 401 Unauthorized
     */
    public static void unauthorized(RoutingContext ctx) {
        LOG.warn("401 Unauthorized: {}", ctx.request().path());
        ctx.response()
            .setStatusCode(401)
            .putHeader("Content-Type", "application/json")
            .end("{\"error\":\"Unauthorized\"}");
    }

    /**
     * Handle 403 Forbidden
     */
    public static void forbidden(RoutingContext ctx) {
        LOG.warn("403 Forbidden: {}", ctx.request().path());
        ctx.response()
            .setStatusCode(403)
            .putHeader("Content-Type", "application/json")
            .end("{\"error\":\"Forbidden\"}");
    }
}