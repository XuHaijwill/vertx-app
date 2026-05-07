package com.example.auth;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Vert.x equivalent of RuoYi's SecurityUtils.
 *
 * <p>Provides static utility methods to access the current authenticated user
 * from within any route handler. The user context is set by KeycloakAuthHandler
 * after JWT validation.</p>
 *
 * <h3>RuoYi → vertx-app mapping:</h3>
 * <pre>
 * SecurityUtils.getUserId()     → AuthUtils.getUserId(ctx)
 * SecurityUtils.getUsername()  → AuthUtils.getUsername(ctx)
 * SecurityUtils.getLoginUser() → AuthUtils.getAuthUser(ctx)
 * SecurityUtils.hasPermi(x)    → AuthUtils.hasPermi(ctx, x)
 * SecurityUtils.hasRole(x)      → AuthUtils.hasRole(ctx, x)
 * SecurityUtils.isAdmin()       → AuthUtils.isAdmin(ctx)
 * </pre>
 *
 * <h3>Usage in route handlers:</h3>
 * <pre>
 * private void listUsers(RoutingContext ctx) {
 *     Long userId = AuthUtils.getUserId(ctx);
 *     if (!AuthUtils.hasPermi(ctx, "system:user:list")) {
 *         // ... forbidden
 *     }
 * }
 * </pre>
 */
public class AuthUtils {

    private static final String CTX_KEY = "authUser";

    /** Get the AuthUser from the routing context. Returns null if not authenticated. */
    public static AuthUser getAuthUser(RoutingContext ctx) {
        return ctx.get(CTX_KEY);
    }

    /** Get the AuthUser, throw if not authenticated. */
    public static AuthUser requireAuth(RoutingContext ctx) {
        AuthUser user = ctx.get(CTX_KEY);
        if (user == null) {
            throw new SecurityException("User not authenticated");
        }
        return user;
    }

    /** Get current user ID. */
    public static Long getUserId(RoutingContext ctx) {
        AuthUser user = ctx.get(CTX_KEY);
        return user != null ? user.getUserId() : null;
    }

    /** Get current username. */
    public static String getUsername(RoutingContext ctx) {
        AuthUser user = ctx.get(CTX_KEY);
        return user != null ? user.getUsername() : null;
    }

    /** Get current user's email. */
    public static String getEmail(RoutingContext ctx) {
        AuthUser user = ctx.get(CTX_KEY);
        return user != null ? user.getEmail() : null;
    }

    /** Get current user's display name. */
    public static String getName(RoutingContext ctx) {
        AuthUser user = ctx.get(CTX_KEY);
        return user != null ? user.getName() : null;
    }

    /** Get current user's dept ID. */
    public static Long getDeptId(RoutingContext ctx) {
        AuthUser user = ctx.get(CTX_KEY);
        return user != null ? user.getDeptId() : null;
    }

    /** Get current user's raw JWT token payload. */
    public static JsonObject getRawToken(RoutingContext ctx) {
        AuthUser user = ctx.get(CTX_KEY);
        return user != null ? user.getRawToken() : null;
    }

    /** Check if the current user is the super admin. */
    public static boolean isAdmin(RoutingContext ctx) {
        AuthUser user = ctx.get(CTX_KEY);
        return user != null && user.isAdmin();
    }

    /**
     * Check if current user has a specific permission.
     * @param permission e.g. "system:user:list"
     */
    public static boolean hasPermi(RoutingContext ctx, String permission) {
        AuthUser user = ctx.get(CTX_KEY);
        return user != null && user.hasPermi(permission);
    }

    /**
     * Check if current user has ALL of the given permissions.
     */
    public static boolean hasAllPermi(RoutingContext ctx, String... permissions) {
        AuthUser user = ctx.get(CTX_KEY);
        return user != null && user.hasAllPermi(permissions);
    }

    /**
     * Check if current user has ANY of the given permissions.
     */
    public static boolean hasAnyPermi(RoutingContext ctx, String... permissions) {
        AuthUser user = ctx.get(CTX_KEY);
        return user != null && user.hasAnyPermi(permissions);
    }

    /**
     * Check if current user has a specific role.
     * @param role e.g. "admin"
     */
    public static boolean hasRole(RoutingContext ctx, String role) {
        AuthUser user = ctx.get(CTX_KEY);
        return user != null && user.hasRole(role);
    }

    /**
     * Check if current user has ANY of the given roles.
     */
    public static boolean hasAnyRole(RoutingContext ctx, String... roles) {
        AuthUser user = ctx.get(CTX_KEY);
        return user != null && user.hasAnyRole(roles);
    }

    /**
     * Build a standard 401 response.
     */
    public static void sendUnauthorized(RoutingContext ctx, String message) {
        ctx.response()
            .setStatusCode(401)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject()
                .put("code", "UNAUTHORIZED")
                .put("message", message != null ? message : "Authentication required")
                .put("timestamp", System.currentTimeMillis())
                .encode());
    }

    /**
     * Build a standard 403 response.
     */
    public static void sendForbidden(RoutingContext ctx, String message) {
        ctx.response()
            .setStatusCode(403)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject()
                .put("code", "FORBIDDEN")
                .put("message", message != null ? message : "Insufficient permissions")
                .put("timestamp", System.currentTimeMillis())
                .encode());
    }

    /**
     * Check if the user is authenticated. If not, send 401 and return false.
     */
    public static boolean checkAuth(RoutingContext ctx) {
        if (ctx.get(CTX_KEY) == null) {
            sendUnauthorized(ctx, "Authentication required");
            return false;
        }
        return true;
    }
}
