package com.example.api;

import com.example.core.ApiResponse;
import com.example.auth.AuthConfig;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * Authentication API — /api/auth/*
 *
 * <p>Provides endpoints for:</p>
 * <ul>
 *   <li>GET /api/auth/config — Keycloak client config for frontend (public info only)</li>
 *   <li>GET /api/auth/me — Current user info (requires valid Bearer token)</li>
 *   <li>POST /api/auth/logout — Logout URL builder</li>
 * </ul>
 */
public class AuthApi extends BaseApi {

    private final AuthConfig authConfig;

    public AuthApi(Vertx vertx, AuthConfig authConfig) {
        super(vertx);
        this.authConfig = authConfig;
    }

    @Override
    public void registerRoutes(Router router, String contextPath) {
        router.get(contextPath + "/api/auth/config").handler(this::getPublicConfig);
        router.get(contextPath + "/api/auth/me").handler(this::getCurrentUser);
        router.post(contextPath + "/api/auth/logout").handler(this::logout);
    }

    /**
     * GET /api/auth/config
     * Returns public Keycloak configuration for frontend initialization.
     * This is safe to expose — no secrets, only public client info.
     */
    private void getPublicConfig(RoutingContext ctx) {
        JsonObject config = new JsonObject()
            .put("enabled", authConfig.isEnabled())
            .put("realm", authConfig.getRealm())
            .put("clientId", authConfig.getClientId())
            .put("authServerUrl", authConfig.getAuthServerUrl())
            .put("publicClient", authConfig.isPublicClient());

        if (authConfig.isEnabled()) {
            config
                .put("authorizationUrl", authConfig.getAuthorizationUrl())
                .put("tokenUrl", authConfig.getTokenUrl())
                .put("logoutUrl", authConfig.getLogoutUrl())
                .put("userInfoUrl", authConfig.getUserInfoUrl());
        }

        ok(ctx, config);
    }

    /**
     * GET /api/auth/me
     * Returns the current authenticated user info extracted from the JWT.
     * Requires KeycloakAuthHandler to have run first.
     */
    private void getCurrentUser(RoutingContext ctx) {
        JsonObject user = ctx.get("user");
        if (user == null) {
            // Auth not enabled or not configured
            ctx.json(new ApiResponse()
                .success(new JsonObject().put("authenticated", false).put("message", "Authentication not enabled"))
                .toJson());
            return;
        }

        String username = ctx.get("username");
        @SuppressWarnings("unchecked")
        java.util.Set<String> roles = ctx.get("roles");

        JsonObject data = new JsonObject()
            .put("authenticated", true)
            .put("username", username)
            .put("subject", user.getString("sub"))
            .put("email", user.getString("email"))
            .put("name", user.getString("name"))
            .put("givenName", user.getString("given_name"))
            .put("familyName", user.getString("family_name"))
            .put("issuer", user.getString("iss"))
            .put("tokenType", user.getString("typ"))
            .put("expiresAt", user.getLong("exp"));

        // Extra top-level keys: roles as permission, total role count
        ctx.json(new ApiResponse()
            .success(data)
            .putExtra("permission", roles != null ? new JsonArray(new java.util.ArrayList<>(roles)) : new JsonArray())
            .putExtra("rolesCount", roles != null ? roles.size() : 0)
            .toJson());
    }

    /**
     * POST /api/auth/logout
     * Returns the Keycloak logout URL for the frontend to redirect to.
     * Optionally accepts a redirect_uri in the body.
     */
    private void logout(RoutingContext ctx) {
        String redirectUri = null;
        try {
            JsonObject body = bodyJson(ctx);
            if (body != null) {
                redirectUri = body.getString("redirect_uri");
            }
        } catch (Exception ignored) {
            // No body — that's fine
        }

        String logoutUrl = authConfig.getLogoutUrl();
        if (logoutUrl == null || logoutUrl.isEmpty()) {
            ctx.json(new ApiResponse()
                .success(new JsonObject().put("logoutUrl", "").put("message", "Logout URL not configured"))
                .toJson());
            return;
        }

        StringBuilder url = new StringBuilder(logoutUrl);
        if (authConfig.getClientId() != null && !authConfig.getClientId().isEmpty()) {
            url.append("?client_id=").append(authConfig.getClientId());
        }
        if (redirectUri != null && !redirectUri.isEmpty()) {
            url.append("&post_logout_redirect_uri=").append(redirectUri);
        }

        ctx.json(new ApiResponse()
            .success(new JsonObject().put("logoutUrl", url.toString()))
            .toJson());
    }
}
