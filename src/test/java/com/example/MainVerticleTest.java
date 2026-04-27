package com.example;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Tests for MainVerticle
 */
@ExtendWith(VertxExtension.class)
class MainVerticleTest {

    private WebClient webClient;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        // Deploy verticle
        vertx.deployVerticle(new MainVerticle())
            .onComplete(testContext.succeeding(id -> {
                // Create web client
                webClient = WebClient.create(vertx, new WebClientOptions()
                    .setDefaultHost("localhost")
                    .setDefaultPort(8888)
                );
                testContext.completeNow();
            }));
    }

    @Test
    void testHealthEndpoint(Vertx vertx, VertxTestContext testContext) {
        webClient.get("/health")
            .send()
            .onComplete(testContext.succeeding(response -> {
                testContext.verify(() -> {
                    assertEquals(200, response.statusCode());
                    JsonObject body = response.bodyAsJsonObject();
                    // Response is wrapped in ApiResponse: {code, message, data}
                    assertEquals("success", body.getString("code"));
                    JsonObject data = body.getJsonObject("data");
                    assertEquals("UP", data.getString("status"));
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testAuthConfigEndpoint(Vertx vertx, VertxTestContext testContext) {
        webClient.get("/api/auth/config")
            .send()
            .onComplete(testContext.succeeding(response -> {
                testContext.verify(() -> {
                    assertEquals(200, response.statusCode());
                    JsonObject body = response.bodyAsJsonObject();
                    // Auth config should be returned (enabled=false by default in tests)
                    assertNotNull(body.getJsonObject("data"));
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testGetUsers(Vertx vertx, VertxTestContext testContext) {
        webClient.get("/api/users")
            .send()
            .onComplete(testContext.succeeding(response -> {
                testContext.verify(() -> {
                    assertEquals(200, response.statusCode());
                    JsonObject body = response.bodyAsJsonObject();
                    assertEquals("success", body.getString("code"));
                    // Response is paginated: {list, total, page, size, pages}
                    JsonObject data = body.getJsonObject("data");
                    assertNotNull(data.getJsonArray("list"));
                    assertTrue(data.getLong("total") >= 0);
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testCreateUser(Vertx vertx, VertxTestContext testContext) {
        var userJson = new JsonObject()
            .put("name", "Test User")
            .put("email", "test-" + System.currentTimeMillis() + "@example.com");

        webClient.post("/api/users")
            .sendJsonObject(userJson)
            .onComplete(testContext.succeeding(response -> {
                testContext.verify(() -> {
                    assertEquals(201, response.statusCode());
                    JsonObject body = response.bodyAsJsonObject();
                    assertEquals("success", body.getString("code"));
                    testContext.completeNow();
                });
            }));
    }
}
