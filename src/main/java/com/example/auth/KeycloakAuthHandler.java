package com.example.auth;

import com.example.cache.TokenCacheManager;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.authentication.Credentials;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2Options;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Keycloak JWT Authentication Handler for Vert.x 5.
 *
 * <p>Validates Bearer tokens against a Keycloak JWKS endpoint.
 * On successful validation, stores user info in the routing context
 * for downstream handlers to access.</p>
 *
 * <h3>Cache Strategy:</h3>
 * <p>Uses Ehcache to cache validated tokens. When a token is validated successfully,
 * the user info (principal, roles, username) is cached until the token expires.
 * Subsequent requests with the same token will use cached data, avoiding
 * repeated JWKS validation calls.</p>
 *
 * <h3>Usage in MainVerticle:</h3>
 * <pre>
 * KeycloakAuthHandler auth = KeycloakAuthHandler.create(vertx, authConfig);
 * // Protect specific routes
 * router.route("/api/*").handler(auth);
 * // Or skip health endpoints
 * router.route("/api/users/*").handler(auth);
 * </pre>
 *
 * <h3>Accessing user info in downstream handlers:</h3>
 * <pre>
 * JsonObject user = ctx.get("user");
 * String username = ctx.get("preferred_username");
 * Set&lt;String&gt; roles = ctx.get("roles");
 * </pre>
 */
public class KeycloakAuthHandler implements Handler<RoutingContext> {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakAuthHandler.class);

    private final OAuth2Auth oauth2;
    private final AuthConfig authConfig;
    private final Set<String> skipPaths;
    private final TokenCacheManager cacheManager;

    private KeycloakAuthHandler(OAuth2Auth oauth2, AuthConfig authConfig, Set<String> skipPaths) {
        this.oauth2 = oauth2;
        this.authConfig = authConfig;
        this.skipPaths = skipPaths;
        this.cacheManager = TokenCacheManager.getInstance();
    }

    /**
     * Create a KeycloakAuthHandler with the given config.
     *
     * @param vertx      Vert.x instance
     * @param authConfig Keycloak authentication configuration
     * @return Future of KeycloakAuthHandler (async because JWKS needs to be fetched)
     */
    public static Future<KeycloakAuthHandler> create(Vertx vertx, AuthConfig authConfig) {
        return create(vertx, authConfig, Collections.emptySet());
    }

    /**
     * Create a KeycloakAuthHandler with paths to skip (e.g. health endpoints).
     *
     * @param vertx      Vert.x instance
     * @param authConfig Keycloak authentication configuration
     * @param skipPaths  Set of path prefixes to skip authentication for
     * @return Future of KeycloakAuthHandler
     */
    public static Future<KeycloakAuthHandler> create(Vertx vertx, AuthConfig authConfig, Set<String> skipPaths) {
        LOG.info("[AUTH] Initializing Keycloak OAuth2 - issuer={}, jwksUri={}, clientId={}",
            authConfig.getIssuer(), authConfig.getJwksUri(), authConfig.getClientId());

        OAuth2Options oauth2Options = new OAuth2Options()
            .setClientId(authConfig.getClientId())
            .setSite(authConfig.getIssuer())
            .setJWTOptions(new JWTOptions()
                .setIssuer(authConfig.getIssuer())
                // Only accept RS256 algorithm — avoids "Unsupported JWK: RSA-OAEP" error.
                .setAlgorithm("RS256")
            );

        // Set audience if configured
        if (authConfig.getAudience() != null && !authConfig.getAudience().isEmpty()) {
            oauth2Options.getJWTOptions().setAudience(List.of(authConfig.getAudience()));
        }

        OAuth2Auth oauth2 = OAuth2Auth.create(vertx, oauth2Options);

        // Load JWKS keys from Keycloak
        return oauth2.jWKSet()
            .map(v -> {
                LOG.info("[AUTH] JWKS keys loaded from Keycloak");
                return new KeycloakAuthHandler(oauth2, authConfig, skipPaths);
            })
            .onFailure(err -> {
                LOG.error("[AUTH] Failed to load JWKS keys from Keycloak: {}", err.getMessage());
                LOG.warn("[AUTH] Token validation will fail until JWKS is available");
            })
            .recover(err -> {
                // Return handler anyway - it will reject tokens until JWKS loads
                LOG.warn("[AUTH] Proceeding without JWKS - tokens will be rejected");
                return Future.succeededFuture(new KeycloakAuthHandler(oauth2, authConfig, skipPaths));
            });
    }

    @Override
    public void handle(RoutingContext ctx) {
        // Skip auth for whitelisted paths
        String path = ctx.request().path();
        for (String skip : skipPaths) {
            if (path.startsWith(skip)) {
                ctx.next();
                return;
            }
        }

        // Extract Bearer token
        String authHeader = ctx.request().getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorized(ctx, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            sendUnauthorized(ctx, "Empty Bearer token");
            return;
        }

        // ========== CACHE LOOKUP ==========
        // Check cache first to avoid repeated JWKS validation
        if (cacheManager.isEnabled()) {
            TokenCacheManager.TokenInfo cachedInfo = cacheManager.get(token);
            if (cachedInfo != null) {
                // Use cached token info
                ctx.put("user", cachedInfo.getPrincipal());
                ctx.put("username", cachedInfo.getUsername());
                ctx.put("roles", cachedInfo.getRoles());
                ctx.put("authEnabled", true);
                ctx.put("tokenFromCache", true); // 标记来自缓存

                LOG.debug("[AUTH] Authenticated user from cache: {}, roles: {}",
                    cachedInfo.getUsername(), cachedInfo.getRoles());
                ctx.next();
                return;
            }
        }

        // ========== JWKS VALIDATION ==========
        // Validate token via OAuth2 (cache miss or cache disabled)
        Credentials credentials = new TokenCredentials(token);

        oauth2.authenticate(credentials)
            .onSuccess(user -> {
                // Extract user info from the decoded JWT
                JsonObject tokenInfo = user.principal();
                String username = tokenInfo.getString("preferred_username",
                    tokenInfo.getString("sub", "unknown"));

                // Extract Keycloak roles from realm_access and resource_access
                Set<String> roles = extractRoles(tokenInfo, authConfig.getClientId());

                // Extract token expiration time
                long expiresAt = extractExpiration(tokenInfo);

                // ========== CACHE WRITE ==========
                // Cache the validated token info
                if (cacheManager.isEnabled()) {
                    cacheManager.put(token, tokenInfo, roles, username, expiresAt);
                    LOG.debug("[AUTH] Token cached for user: {}, expiresAt: {}",
                        username, new Date(expiresAt));
                }

                // Store user info in context for downstream handlers
                ctx.put("user", tokenInfo);
                ctx.put("username", username);
                ctx.put("roles", roles);
                ctx.put("authEnabled", true);
                ctx.put("tokenFromCache", false); // 标记来自 JWKS 验证

                LOG.debug("[AUTH] Authenticated user: {}, roles: {}", username, roles);
                ctx.next();
            })
            .onFailure(err -> {
                LOG.warn("[AUTH] Token validation failed: {}", err.getMessage());
                if (err.getMessage() != null && err.getMessage().contains("expired")) {
                    sendUnauthorized(ctx, "Token expired");
                } else if (err.getMessage() != null && err.getMessage().contains("Invalid")) {
                    sendUnauthorized(ctx, "Invalid token");
                } else {
                    sendUnauthorized(ctx, "Authentication failed: " + err.getMessage());
                }
            });
    }

    /**
     * Extract expiration time from JWT token.
     * Returns the 'exp' claim value (in milliseconds), or default 1 hour if not present.
     */
    private long extractExpiration(JsonObject tokenInfo) {
        Long exp = tokenInfo.getLong("exp");
        if (exp != null) {
            // exp is in seconds, convert to milliseconds
            return exp * 1000;
        }
        // Default: 1 hour from now
        return System.currentTimeMillis() + 3600 * 1000;
    }

    /**
     * Extract roles from Keycloak JWT token.
     * Keycloak puts roles in two places:
     * - realm_access.roles (realm-level roles)
     * - resource_access.{client-id}.roles (client-level roles)
     */
    private Set<String> extractRoles(JsonObject tokenInfo, String clientId) {
        Set<String> roles = new HashSet<>();

        // Realm-level roles
        JsonObject realmAccess = tokenInfo.getJsonObject("realm_access");
        if (realmAccess != null) {
            var realmRoles = realmAccess.getJsonArray("roles");
            if (realmRoles != null) {
                for (Object role : realmRoles) {
                    roles.add("realm:" + role.toString());
                }
            }
        }

        // Client-level roles
        JsonObject resourceAccess = tokenInfo.getJsonObject("resource_access");
        if (resourceAccess != null && clientId != null && !clientId.isEmpty()) {
            JsonObject clientAccess = resourceAccess.getJsonObject(clientId);
            if (clientAccess != null) {
                var clientRoles = clientAccess.getJsonArray("roles");
                if (clientRoles != null) {
                    for (Object role : clientRoles) {
                        roles.add("client:" + role.toString());
                    }
                }
            }
        }

        // Also add simple role names (without prefix) for convenience
        Set<String> simpleRoles = roles.stream()
            .map(r -> r.contains(":") ? r.substring(r.indexOf(":") + 1) : r)
            .collect(Collectors.toSet());
        roles.addAll(simpleRoles);

        return roles;
    }

    /**
     * Send 401 Unauthorized response.
     */
    private void sendUnauthorized(RoutingContext ctx, String reason) {
        ctx.response()
            .setStatusCode(401)
            .putHeader("Content-Type", "application/json")
            .putHeader("WWW-Authenticate", "Bearer realm=\"" + authConfig.getRealm() + "\"")
            .end(new JsonObject()
                .put("code", "UNAUTHORIZED")
                .put("message", reason)
                .put("timestamp", System.currentTimeMillis())
                .encode());
    }

    /**
     * Create a role-based authorization handler.
     * Must be used AFTER KeycloakAuthHandler in the route chain.
     *
     * @param requiredRoles One or more roles - user must have at least one
     * @return Handler that checks roles
     */
    public static Handler<RoutingContext> requireRole(String... requiredRoles) {
        Set<String> required = Set.of(requiredRoles);
        return ctx -> {
            @SuppressWarnings("unchecked")
            Set<String> userRoles = ctx.get("roles");
            if (userRoles == null) {
                ctx.response()
                    .setStatusCode(403)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("code", "FORBIDDEN")
                        .put("message", "No roles found - authentication required")
                        .put("timestamp", System.currentTimeMillis())
                        .encode());
                return;
            }

            boolean hasRole = required.stream().anyMatch(userRoles::contains);
            if (hasRole) {
                ctx.next();
            } else {
                ctx.response()
                    .setStatusCode(403)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("code", "FORBIDDEN")
                        .put("message", "Insufficient permissions. Required: " + required)
                        .put("timestamp", System.currentTimeMillis())
                        .encode());
            }
        };
    }
}
