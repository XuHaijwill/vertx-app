package com.example.auth;

import io.vertx.core.json.JsonObject;

/**
 * Keycloak / JWT authentication configuration model.
 * Loaded from YAML: auth.* keys.
 *
 * <p>When {@code auth.enabled=true}, the application validates JWT access tokens
 * issued by a Keycloak server against its JWKS endpoint. Requests without a
 * valid Bearer token receive 401 Unauthorized.</p>
 *
 * <p>When {@code auth.enabled=false} (default), all endpoints are open — suitable
 * for local development and testing.</p>
 */
public class AuthConfig {

    // Flat-key constants (for direct config.getString style)
    public static final String KEY_ENABLED  = "auth.enabled";
    public static final String KEY_JWKS_URI = "auth.jwks-uri";
    public static final String KEY_ISSUER   = "auth.issuer";
    public static final String KEY_CLIENT   = "auth.client-id";
    public static final String KEY_AUDIENCE = "auth.audience";
    public static final String KEY_ROLES    = "auth.default-roles";
    public static final String KEY_REALM    = "auth.realm";
    public static final String KEY_AUTH_SERVER_URL = "auth.auth-server-url";
    public static final String KEY_PUBLIC_CLIENT   = "auth.public-client";

    /** Whether JWT auth is enabled. Default: false (open). */
    public boolean enabled;

    /**
     * JWKS URI — the public key endpoint exposed by your Keycloak server.
     * e.g. http://localhost:8180/realms/myrealm/protocol/openid-connect/certs
     */
    public String jwksUri;

    /**
     * Expected JWT issuer — must match the 'iss' claim.
     * e.g. http://localhost:8180/realms/myrealm
     */
    public String issuer;

    /**
     * Keycloak client ID that this service trusts.
     */
    public String clientId;

    /**
     * Expected audience — must match the 'aud' claim.
     */
    public String audience;

    /**
     * Default role assigned to any authenticated user (comma-separated for multiple).
     */
    public String defaultRoles;

    /**
     * Keycloak realm name.
     */
    public String realm;

    /**
     * Keycloak auth server base URL.
     * e.g. http://localhost:8180
     */
    public String authServerUrl;

    /**
     * Whether this is a public client (no client secret needed).
     */
    public boolean publicClient;

    public static AuthConfig from(JsonObject config) {
        AuthConfig ac = new AuthConfig();

        // Try nested "auth" object first, then fall back to flat keys
        JsonObject auth = config.getJsonObject("auth");
        if (auth != null) {
            ac.enabled      = auth.getBoolean("enabled", false);
            ac.jwksUri      = auth.getString("jwks-uri", "");
            ac.issuer       = auth.getString("issuer", "");
            ac.clientId     = auth.getString("client-id", "");
            ac.audience     = auth.getString("audience", "");
            ac.defaultRoles = auth.getString("default-roles", "user");
            ac.realm        = auth.getString("realm", "");
            ac.authServerUrl= auth.getString("auth-server-url", "");
            ac.publicClient = auth.getBoolean("public-client", true);
        } else {
            // Flat-key fallback
            ac.enabled      = config.getBoolean(KEY_ENABLED, false);
            ac.jwksUri      = config.getString(KEY_JWKS_URI, "");
            ac.issuer       = config.getString(KEY_ISSUER, "");
            ac.clientId     = config.getString(KEY_CLIENT, "");
            ac.audience     = config.getString(KEY_AUDIENCE, "");
            ac.defaultRoles = config.getString(KEY_ROLES, "user");
            ac.realm        = config.getString(KEY_REALM, "");
            ac.authServerUrl= config.getString(KEY_AUTH_SERVER_URL, "");
            ac.publicClient = config.getBoolean(KEY_PUBLIC_CLIENT, true);
        }

        // Auto-derive issuer / jwksUri from authServerUrl + realm if not set
        if (ac.issuer.isEmpty() && !ac.authServerUrl.isEmpty() && !ac.realm.isEmpty()) {
            ac.issuer = ac.authServerUrl + "/realms/" + ac.realm;
        }
        if (ac.jwksUri.isEmpty() && !ac.issuer.isEmpty()) {
            ac.jwksUri = ac.issuer + "/protocol/openid-connect/certs";
        }

        return ac;
    }

    /**
     * Build the Keycloak token introspection URL.
     */
    public String getIntrospectionUrl() {
        if (issuer.isEmpty()) return "";
        return issuer + "/protocol/openid-connect/token/introspect";
    }

    /**
     * Build the Keycloak authorization URL.
     */
    public String getAuthorizationUrl() {
        if (issuer.isEmpty()) return "";
        return issuer + "/protocol/openid-connect/auth";
    }

    /**
     * Build the Keycloak token URL.
     */
    public String getTokenUrl() {
        if (issuer.isEmpty()) return "";
        return issuer + "/protocol/openid-connect/token";
    }

    /**
     * Build the Keycloak userinfo URL.
     */
    public String getUserInfoUrl() {
        if (issuer.isEmpty()) return "";
        return issuer + "/protocol/openid-connect/userinfo";
    }

    /**
     * Build the Keycloak logout URL.
     */
    public String getLogoutUrl() {
        if (issuer.isEmpty()) return "";
        return issuer + "/protocol/openid-connect/logout";
    }

    // ================================================================
    // Getters
    // ================================================================

    public boolean isEnabled()      { return enabled; }
    public String getJwksUri()      { return jwksUri; }
    public String getIssuer()       { return issuer; }
    public String getClientId()     { return clientId; }
    public String getAudience()     { return audience; }
    public String getDefaultRoles() { return defaultRoles; }
    public String getRealm()        { return realm; }
    public String getAuthServerUrl(){ return authServerUrl; }
    public boolean isPublicClient() { return publicClient; }
}
