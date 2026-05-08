package com.example.auth;

import com.example.cache.TokenCacheManager;
import com.example.db.AuditContext;
import com.example.db.AuditContextHolder;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.ext.auth.authentication.Credentials;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Keycloak JWT Authentication Handler for Vert.x 5.
 *
 * <p>Validates Bearer tokens using JWTAuth with JWKS keys fetched
 * from the Keycloak /protocol/openid-connect/certs endpoint.
 * On successful validation, stores user info in the routing context.</p>
 *
 * <h3>Cache Strategy:</h3>
 * <p>Uses Ehcache to cache validated tokens. When a token is validated successfully,
 * the user info (principal, roles, username) is cached until the token expires.
 * Subsequent requests with the same token will use cached data, avoiding
 * repeated JWKS validation calls.</p>
 */
public class KeycloakAuthHandler implements io.vertx.core.Handler<RoutingContext> {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakAuthHandler.class);

    private final AuthenticationProvider authProvider;
    private final AuthConfig authConfig;
    private final Set<String> skipPaths;
    private final TokenCacheManager cacheManager;

    private KeycloakAuthHandler(AuthenticationProvider authProvider, AuthConfig authConfig, Set<String> skipPaths) {
        this.authProvider = authProvider;
        this.authConfig = authConfig;
        this.skipPaths = skipPaths;
        this.cacheManager = TokenCacheManager.getInstance();
    }

    /**
     * Create a KeycloakAuthHandler with the given config.
     * Async — fetches JWKS keys on startup.
     */
    public static Future<KeycloakAuthHandler> create(Vertx vertx, AuthConfig authConfig) {
        return create(vertx, authConfig, Collections.emptySet());
    }

    /**
     * Create a KeycloakAuthHandler with paths to skip (e.g. health endpoints).
     * Fetches JWKS keys from the Keycloak certs endpoint.
     */
    public static Future<KeycloakAuthHandler> create(Vertx vertx, AuthConfig authConfig, Set<String> skipPaths) {
        LOG.info("[AUTH] Initializing Keycloak JWT auth — issuer={}, jwksUri={}",
            authConfig.getIssuer(), authConfig.getJwksUri());

        WebClient webClient = WebClient.create(vertx);

        // Step 1: Fetch JWKS from Keycloak
        return webClient.getAbs(authConfig.getJwksUri())
            .putHeader("Accept", "application/json")
            .send()
            .compose(resp -> {
                if (resp.statusCode() != 200) {
                    return Future.failedFuture("JWKS fetch failed: HTTP " + resp.statusCode());
                }
                JsonObject body = resp.bodyAsJsonObject();
                JsonArray keys = body.getJsonArray("keys");
                if (keys == null || keys.isEmpty()) {
                    return Future.failedFuture("JWKS response has no keys");
                }
                LOG.info("[AUTH] JWKS fetched: {} keys", keys.size());

                // Step 2: Build JWTAuth with the fetched keys
                // Vert.x 5 JsonObject no longer implements Map — use encode/decode to convert
                List<JsonObject> jwkList = keys.stream()
                    .map(obj -> obj instanceof JsonObject jo ? jo : new JsonObject(obj.toString()))
                    .collect(Collectors.toList());

                JWTAuthOptions jwtOptions = new JWTAuthOptions()
                    .setJwks(jwkList)
                    .setJWTOptions(new JWTOptions()
                        .setIssuer(authConfig.getIssuer())
                        .setAlgorithm("RS256")
                    );

                if (authConfig.getAudience() != null && !authConfig.getAudience().isEmpty()) {
                    jwtOptions.getJWTOptions().setAudience(List.of(authConfig.getAudience()));
                }

                JWTAuth jwtAuth = JWTAuth.create(vertx, jwtOptions);

                return Future.succeededFuture(new KeycloakAuthHandler(jwtAuth, authConfig, skipPaths));
            })
            .onSuccess(handler -> {
                LOG.info("[AUTH] Keycloak auth handler initialized successfully");
            })
            .onFailure(err -> {
                LOG.error("[AUTH] JWKS fetch failed — type: {}, message: {}", err.getClass().getName(), err.getMessage());
                if (err.getCause() != null) {
                    LOG.error("[AUTH]   caused by: {}: {}", err.getCause().getClass().getName(), err.getCause().getMessage());
                }
            })
            .recover(err -> {
                // Proceed anyway — first request will fail auth but won't crash
                LOG.warn("[AUTH] Proceeding without JWKS preload — tokens will be rejected until JWKS loads");
                JWTAuthOptions emptyOptions = new JWTAuthOptions()
                    .setJWTOptions(new JWTOptions().setIssuer(authConfig.getIssuer()));
                JWTAuth fallbackAuth = JWTAuth.create(vertx, emptyOptions);
                return Future.succeededFuture(new KeycloakAuthHandler(fallbackAuth, authConfig, skipPaths));
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
        if (cacheManager.isEnabled()) {
            TokenCacheManager.TokenInfo cachedInfo = cacheManager.get(token);
            if (cachedInfo != null) {
                ctx.put("user", cachedInfo.getPrincipal());
                ctx.put("username", cachedInfo.getUsername());
                ctx.put("roles", cachedInfo.getRoles());
                ctx.put("authEnabled", true);
                ctx.put("tokenFromCache", true);

                // Restore AuthUser from cached info
                AuthUser authUser = new AuthUser()
                    .setUsername(cachedInfo.getUsername())
                    .setRoles(cachedInfo.getRoles())
                    .setRawToken(cachedInfo.getPrincipal());
                Long uid = cachedInfo.getPrincipal().getLong("user_id");
                if (uid == null) {
                    String sub = cachedInfo.getPrincipal().getString("sub");
                    if (sub != null) uid = hashToLong(sub);
                }
                authUser.setUserId(uid);
                ctx.put("authUser", authUser);

                LOG.debug("[AUTH] Token validated from cache: {}", cachedInfo.getUsername());
                ctx.next();
                return;
            }
        }

        // ========== JWT VALIDATION via JWTAuth ==========
        Credentials credentials = new TokenCredentials(token);
        authProvider.authenticate(credentials)
            .onSuccess(user -> {
                JsonObject tokenInfo = user.principal();
                String username = tokenInfo.getString("preferred_username",
                    tokenInfo.getString("sub", "unknown"));

                Set<String> roles = extractRoles(tokenInfo, authConfig.getClientId());
                long expiresAt = extractExpiration(tokenInfo);

                // Cache the validated token
                if (cacheManager.isEnabled()) {
                    cacheManager.put(token, tokenInfo, roles, username, expiresAt);
                }

                // Build AuthUser for the permission system
                AuthUser authUser = new AuthUser()
                    .setUsername(username)
                    .setEmail(tokenInfo.getString("email"))
                    .setName(tokenInfo.getString("name"))
                    .setRoles(roles)
                    .setRawToken(tokenInfo);

                // Try to get userId from custom claim "user_id" (Long)
                // or hash the "sub" claim to a numeric value
                Long userId = tokenInfo.getLong("user_id");
                if (userId == null) {
                    String sub = tokenInfo.getString("sub");
                    if (sub != null) userId = hashToLong(sub);
                }
                authUser.setUserId(userId);

                ctx.put("user", tokenInfo);
                ctx.put("username", username);
                ctx.put("roles", roles);
                ctx.put("authEnabled", true);
                ctx.put("tokenFromCache", false);
                // AuthUser for the permission system
                ctx.put("authUser", authUser);

                // Bind AuditContext for the full request lifecycle
                String traceId = tokenInfo.getString("jti");
                String reqId = ctx.get("requestId");
                String fwd = ctx.request().getHeader("X-Forwarded-For");
                String realIp = ctx.request().getHeader("X-Real-IP");
                String remoteAddr = ctx.request().remoteAddress() != null ? ctx.request().remoteAddress().host() : null;
                String ua = ctx.request().getHeader("User-Agent");

                AuditContext auditCtx = new AuditContext()
                    .setUserId(userId)
                    .setUsername(username)
                    .setOrGenerateTraceId(traceId, reqId)
                    .setRequestId(reqId)
                    .setUserIpFromHeader(fwd, realIp, remoteAddr)
                    .setUserAgent(ua)
                    .setServiceName("vertx-app");
                AuditContextHolder.bind(auditCtx);

                LOG.debug("[AUTH] JWT validated: {}, roles: {}", username, roles);
                ctx.next();
            })
            .onFailure(err -> {
                LOG.warn("[AUTH] Token validation failed: {}", err.getMessage());
                String msg = err.getMessage() != null ? err.getMessage() : "Invalid token";
                sendUnauthorized(ctx, "Authentication failed: " + msg);
            });
    }

    private long extractExpiration(JsonObject tokenInfo) {
        Long exp = tokenInfo.getLong("exp");
        if (exp != null) return exp * 1000;
        return System.currentTimeMillis() + 3600_000;
    }

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

        // Simple role names too
        Set<String> simpleRoles = roles.stream()
            .map(r -> r.contains(":") ? r.substring(r.indexOf(":") + 1) : r)
            .collect(Collectors.toSet());
        roles.addAll(simpleRoles);

        return roles;
    }

    private static long hashToLong(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            long h = 0;
            for (int i = 0; i < 8; i++) {
                h = (h << 8) | (hash[i] & 0xFF);
            }
            return Math.abs(h);
        } catch (NoSuchAlgorithmException e) {
            return input.hashCode();
        }
    }

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
     * Role-based authorization handler — must be used AFTER KeycloakAuthHandler.
     */
    public static io.vertx.core.Handler<RoutingContext> requireRole(String... requiredRoles) {
        Set<String> required = Set.of(requiredRoles);
        return ctx -> {
            @SuppressWarnings("unchecked")
            Set<String> userRoles = ctx.get("roles");
            if (userRoles == null || userRoles.isEmpty()) {
                ctx.response()
                    .setStatusCode(403)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("code", "FORBIDDEN")
                        .put("message", "No roles found — authentication required")
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
                        .put("message", "Insufficient permissions. Required: " + required + ", got: " + userRoles)
                        .put("timestamp", System.currentTimeMillis())
                        .encode());
            }
        };
    }
}
