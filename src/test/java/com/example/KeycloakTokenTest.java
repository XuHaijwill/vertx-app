package com.example;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keycloak token acquisition test.
 *
 * <p>Tests: POST to Keycloak token endpoint with username/password,
 * receive and decode access_token.</p>
 *
 * <p>Run with: mvn test -Dtest=KeycloakTokenTest</p>
 *
 * <p>Required env / config:</p>
 * <ul>
 *   <li>KEYCLOAK_SERVER  — auth server URL, e.g. http://192.168.60.134:8080</li>
 *   <li>KEYCLOAK_REALM   — realm name, e.g. myrealm</li>
 *   <li>KEYCLOAK_CLIENT  — client ID, e.g. vertx-app</li>
 *   <li>KEYCLOAK_USER    — test username</li>
 *   <li>KEYCLOAK_PWD     — test password</li>
 * </ul>
 */
public class KeycloakTokenTest {

    // ── Config ─────────────────────────────────────────────────────────────────
    // Override via system properties or .env before running.
    // Default values match application.yml defaults.
    private static final String KEYCLOAK_SERVER =
        getenv("KEYCLOAK_SERVER", "http://192.168.60.134:8080");
    private static final String KEYCLOAK_REALM =
        getenv("KEYCLOAK_REALM", "myrealm");
    private static final String KEYCLOAK_CLIENT =
        getenv("KEYCLOAK_CLIENT", "vertx-app");
    private static final String KEYCLOAK_USER =
        getenv("KEYCLOAK_USER", "test1");
    private static final String KEYCLOAK_PWD =
        getenv("KEYCLOAK_PWD", "Test123456");

    // Token endpoint: {server}/realms/{realm}/protocol/openid-connect/token
    private static final String TOKEN_URL =
        KEYCLOAK_SERVER + "/realms/" + KEYCLOAK_REALM + "/protocol/openid-connect/token";

    @Test
    void acquireToken_withPassword() throws Exception {
        Vertx vertx = Vertx.vertx();
        WebClient client = WebClient.create(vertx);

        AtomicReference<String> accessToken = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        // 1. POST username/password to Keycloak token endpoint
        JsonObject form = new JsonObject()
            .put("grant_type", "password")
            .put("client_id", KEYCLOAK_CLIENT)
            .put("username", KEYCLOAK_USER)
            .put("password", KEYCLOAK_PWD);

        client.postAbs(TOKEN_URL)
            .putHeader("Content-Type", "application/x-www-form-urlencoded")
            .sendForm(formToMultiMap(form))
            .onComplete(ar -> {
                if (ar.failed()) {
                    System.err.println("[FAIL] Request error: " + ar.cause().getMessage());
                    latch.countDown();
                    return;
                }

                HttpResponse<Buffer> resp = ar.result();
                System.out.println("[HTTP] status=" + resp.statusCode());
                System.out.println("[HTTP] body=" + resp.bodyAsString());

                if (resp.statusCode() != 200) {
                    latch.countDown();
                    return;
                }

                JsonObject body = resp.bodyAsJsonObject();
                String token = body.getString("access_token");
                System.out.println(token);

                if (token == null) {
                    System.err.println("[FAIL] access_token is null");
                    latch.countDown();
                    return;
                }

                accessToken.set(token);
                System.out.println("[OK] access_token received, length=" + token.length());

                // 2. Decode JWT (no signature verification — just peek at claims)
                decodeAndPrint(token);

                latch.countDown();
            });

        boolean ok = latch.await(15, TimeUnit.SECONDS);
        vertx.close();

        if (!ok) {
            throw new AssertionError("Timeout waiting for Keycloak response");
        }
        if (accessToken.get() == null) {
            throw new AssertionError("access_token was null — check Keycloak config");
        }
        System.out.println("[PASS] Token acquired successfully");
    }

    /**
     * Decode JWT payload without verification.
     * Prints: sub, iss, exp, iat, roles, email, name
     */
    private static void decodeAndPrint(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                System.err.println("[WARN] Invalid JWT format");
                return;
            }
            // Use URL-safe Base64 decoder which handles '-' and '_' and padding correctly
            byte[] payloadBytes = java.util.Base64.getUrlDecoder().decode(parts[1]);
            Buffer payloadBuf = Buffer.buffer(payloadBytes);
            JsonObject claims = payloadBuf.toJsonObject();

            System.out.println("\n=== JWT Claims ===");
            System.out.println("sub       : " + claims.getString("sub"));
            System.out.println("iss       : " + claims.getString("iss"));
            System.out.println("exp       : " + claims.getLong("exp") + "  (" + java.time.Instant.ofEpochSecond(claims.getLong("exp")) + ")");
            System.out.println("iat       : " + claims.getLong("iat") + "  (" + java.time.Instant.ofEpochSecond(claims.getLong("iat")) + ")");
            System.out.println("email     : " + claims.getString("email"));
            System.out.println("name      : " + claims.getString("name"));
            System.out.println("preferred_username: " + claims.getString("preferred_username"));

            // Keycloak stores roles under "realm_access" -> "roles"
            JsonObject realmAccess = claims.getJsonObject("realm_access");
            if (realmAccess != null) {
                System.out.println("realm_roles: " + realmAccess.getJsonArray("roles"));
            }
            JsonObject resourceAccess = claims.getJsonObject("resource_access");
            if (resourceAccess != null) {
                System.out.println("resource_access: " + resourceAccess.encode());
            }
            System.out.println("==================\n");

        } catch (Exception e) {
            System.err.println("[WARN] Could not decode JWT: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String getenv(String key, String def) {
        String v = System.getProperty(key);
        if (v != null) return v;
        v = System.getenv(key);
        return v != null ? v : def;
    }

    /**
     * Vert.x WebClient sendForm requires MultiMap (URL-encoded form body).
     */
    private static MultiMap formToMultiMap(JsonObject obj) {
        MultiMap mm = MultiMap.caseInsensitiveMultiMap();
        obj.forEach(e -> mm.add(e.getKey(), String.valueOf(e.getValue())));
        return mm;
    }
}