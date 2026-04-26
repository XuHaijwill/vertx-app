package com.example;

import io.vertx.core.Vertx;
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
                    assertTrue(response.bodyAsString().contains("UP"));
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testHelloEndpoint(Vertx vertx, VertxTestContext testContext) {
        webClient.get("/api/hello")
            .send()
            .onComplete(testContext.succeeding(response -> {
                testContext.verify(() -> {
                    assertEquals(200, response.statusCode());
                    assertTrue(response.bodyAsString().contains("Hello"));
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
                    assertNotNull(response.bodyAsJsonArray());
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testCreateUser(Vertx vertx, VertxTestContext testContext) {
        var userJson = new io.vertx.core.json.JsonObject()
            .put("name", "Test User")
            .put("email", "test@example.com");

        webClient.post("/api/users")
            .sendJsonObject(userJson)
            .onComplete(testContext.succeeding(response -> {
                testContext.verify(() -> {
                    assertEquals(201, response.statusCode());
                    testContext.completeNow();
                });
            }));
    }
}