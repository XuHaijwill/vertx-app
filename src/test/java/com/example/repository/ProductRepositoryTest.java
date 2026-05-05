package com.example.repository;

import com.example.MainVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Tests for ProductRepository
 * Uses VertxExtension for Vert.x async test support.
 * Deploys MainVerticle to initialize database pool.
 */
@ExtendWith(VertxExtension.class)
class ProductRepositoryTest {

    private ProductRepository repo;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        // Deploy MainVerticle to initialize database pool
        vertx.deployVerticle(new MainVerticle())
            .onComplete(testContext.succeeding(id -> {
                repo = new ProductRepository(vertx);
                testContext.completeNow();
            }));
    }

    @Test
    void findAll_returnsProducts(Vertx vertx, VertxTestContext testContext) {
        repo.findAll()
            .onComplete(testContext.succeeding(list -> {
                testContext.verify(() -> {
                    assertNotNull(list);
                    // Seed data has 3 products
                    assertTrue(list.size() >= 3);
                });
                testContext.completeNow();
            }));
    }

    @Test
    void findById_existingId_returnsProduct(Vertx vertx, VertxTestContext testContext) {
        repo.findById(1L)
            .onComplete(testContext.succeeding(product -> {
                testContext.verify(() -> {
                    assertNotNull(product);
                    assertEquals(1L, product.getLong("id").longValue());
                    assertNotNull(product.getString("name"));
                });
                testContext.completeNow();
            }));
    }

    @Test
    void findById_nonExistingId_returnsNull(Vertx vertx, VertxTestContext testContext) {
        repo.findById(99999L)
            .onComplete(testContext.succeeding(product -> {
                testContext.verify(() -> {
                    assertNull(product);
                });
                testContext.completeNow();
            }));
    }

    @Test
    void search_withKeyword_returnsMatchingProducts(Vertx vertx, VertxTestContext testContext) {
        repo.search("laptop", null)
            .onComplete(testContext.succeeding(list -> {
                testContext.verify(() -> {
                    assertNotNull(list);
                    // Seed data includes "Laptop" products
                    assertTrue(list.stream().anyMatch(p -> 
                        p.getString("name", "").toLowerCase().contains("laptop")));
                });
                testContext.completeNow();
            }));
    }

    @Test
    void search_withCategory_returnsMatchingProducts(Vertx vertx, VertxTestContext testContext) {
        repo.search(null, "Electronics")
            .onComplete(testContext.succeeding(list -> {
                testContext.verify(() -> {
                    assertNotNull(list);
                    // All should be Electronics
                    assertTrue(list.stream().allMatch(p -> 
                        "Electronics".equals(p.getString("category"))));
                });
                testContext.completeNow();
            }));
    }

    @Test
    void searchPaginated_returnsPageResults(Vertx vertx, VertxTestContext testContext) {
        repo.searchPaginated(null, null, 1, 2)
            .onComplete(testContext.succeeding(list -> {
                testContext.verify(() -> {
                    assertNotNull(list);
                    assertTrue(list.size() <= 2);
                });
                testContext.completeNow();
            }));
    }

    @Test
    void create_newProduct_returnsCreatedProduct(Vertx vertx, VertxTestContext testContext) {
        JsonObject newProduct = new JsonObject()
            .put("name", "Test Product")
            .put("category", "Test Category")
            .put("price", 99.99)
            .put("stock", 10)
            .put("description", "Test description")
            .put("status", "active");

        repo.create(newProduct)
            .onComplete(testContext.succeeding(created -> {
                testContext.verify(() -> {
                    assertNotNull(created);
                    assertNotNull(created.getLong("id"));
                    assertEquals("Test Product", created.getString("name"));
                    assertEquals("Test Category", created.getString("category"));
                    assertEquals(0, created.getDouble("price").compareTo(99.99));
                });
                testContext.completeNow();
            }));
    }

    @Test
    void update_existingProduct_returnsUpdatedProduct(Vertx vertx, VertxTestContext testContext) {
        // First create
        JsonObject newProduct = new JsonObject()
            .put("name", "Original Name")
            .put("category", "Original")
            .put("price", 50.0)
            .put("stock", 5)
            .put("description", "Original desc")
            .put("status", "active");

        repo.create(newProduct)
            .compose(created -> {
                Long id = created.getLong("id");
                JsonObject updates = new JsonObject()
                    .put("name", "Updated Name")
                    .put("price", 75.0);
                return repo.update(id, updates);
            })
            .onComplete(testContext.succeeding(updated -> {
                testContext.verify(() -> {
                    assertNotNull(updated);
                    assertEquals("Updated Name", updated.getString("name"));
                    assertEquals(0, updated.getDouble("price").compareTo(75.0));
                });
                testContext.completeNow();
            }));
    }

    @Test
    void delete_existingProduct_returnsTrue(Vertx vertx, VertxTestContext testContext) {
        // First create
        JsonObject newProduct = new JsonObject()
            .put("name", "To Delete")
            .put("category", "Test")
            .put("price", 10.0)
            .put("stock", 1)
            .put("description", "Delete me")
            .put("status", "active");

        repo.create(newProduct)
            .compose(created -> repo.delete(created.getLong("id")))
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertTrue(result);
                });
                testContext.completeNow();
            }));
    }

    @Test
    void deleteByIds_multipleIds_returnsDeletedCount(Vertx vertx, VertxTestContext testContext) {
        // Create 2 products then delete by IDs
        JsonObject p1 = new JsonObject()
            .put("name", "Batch Test 1").put("category", "Test").put("price", 10.0).put("stock", 1).put("description", "p1").put("status", "active");
        JsonObject p2 = new JsonObject()
            .put("name", "Batch Test 2").put("category", "Test").put("price", 20.0).put("stock", 2).put("description", "p2").put("status", "active");

        repo.create(p1)
            .compose(created1 -> repo.create(p2))
            .compose(created2 -> {
                // created2 is the result from create(p2)
                // We need both IDs - get them from both results
                // Since we lost created1 reference, query again
                return repo.findByCategory("Test");
            })
            .compose(list -> {
                // Delete all Test products
                List<Long> ids = list.stream().map(p -> p.getLong("id")).toList();
                return repo.deleteByIds(ids);
            })
            .onComplete(testContext.succeeding(count -> {
                testContext.verify(() -> {
                    assertNotNull(count);
                    assertTrue(count >= 2);
                });
                testContext.completeNow();
            }));
    }

    @Test
    void existsById_existingId_returnsTrue(Vertx vertx, VertxTestContext testContext) {
        repo.existsById(1L)
            .onComplete(testContext.succeeding(exists -> {
                testContext.verify(() -> {
                    assertTrue(exists);
                });
                testContext.completeNow();
            }));
    }

    @Test
    void existsById_nonExistingId_returnsFalse(Vertx vertx, VertxTestContext testContext) {
        repo.existsById(99999L)
            .onComplete(testContext.succeeding(exists -> {
                testContext.verify(() -> {
                    assertFalse(exists);
                });
                testContext.completeNow();
            }));
    }

    @Test
    void existsByName_existingName_returnsTrue(Vertx vertx, VertxTestContext testContext) {
        repo.existsByName("Laptop")
            .onComplete(testContext.succeeding(exists -> {
                testContext.verify(() -> {
                    assertTrue(exists);
                });
                testContext.completeNow();
            }));
    }

    @Test
    void existsByName_nonExistingName_returnsFalse(Vertx vertx, VertxTestContext testContext) {
        repo.existsByName("NonExistentProduct12345")
            .onComplete(testContext.succeeding(exists -> {
                testContext.verify(() -> {
                    assertFalse(exists);
                });
                testContext.completeNow();
            }));
    }

    @Test
    void findByCategory_returnsProductsInCategory(Vertx vertx, VertxTestContext testContext) {
        repo.findByCategory("Electronics")
            .onComplete(testContext.succeeding(list -> {
                testContext.verify(() -> {
                    assertNotNull(list);
                    assertTrue(list.size() > 0);
                    list.forEach(p -> assertEquals("Electronics", p.getString("category")));
                });
                testContext.completeNow();
            }));
    }

    @Test
    void count_returnsValidCount(Vertx vertx, VertxTestContext testContext) {
        repo.count()
            .onComplete(testContext.succeeding(count -> {
                testContext.verify(() -> {
                    assertNotNull(count);
                    assertTrue(count >= 3); // seed data
                });
                testContext.completeNow();
            }));
    }
}