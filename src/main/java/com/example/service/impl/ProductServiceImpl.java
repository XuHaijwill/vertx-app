package com.example.service.impl;

import com.example.core.BusinessException;
import com.example.core.PageResult;
import com.example.db.AuditAction;
import com.example.db.AuditLogger;
import com.example.repository.ProductRepository;
import com.example.service.ProductService;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Product Service Implementation - with PostgreSQL
 */
public class ProductServiceImpl implements ProductService {

    private static final Logger LOG = LoggerFactory.getLogger(ProductServiceImpl.class);

    private final ProductRepository productRepository;
    private final AuditLogger audit;
    private final boolean dbAvailable;

    public ProductServiceImpl(Vertx vertx) {
        this.productRepository = new ProductRepository(vertx);
        this.audit = new AuditLogger(vertx);
        this.dbAvailable = checkDbAvailability(vertx);
        
        if (!dbAvailable) {
            LOG.warn("Database not available - using demo mode");
        }
    }

    private boolean checkDbAvailability(Vertx vertx) {
        try {
            return com.example.db.DatabaseVerticle.getPool(vertx) != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Future<List<JsonObject>> findAll() {
        if (!dbAvailable) return Future.succeededFuture(getDemoProducts());
        return productRepository.findAll();
    }

    @Override
    public Future<JsonObject> findById(Long id) {
        if (!dbAvailable) {
            return Future.succeededFuture(getDemoProducts().stream()
                .filter(p -> p.getLong("id").equals(id))
                .findFirst().orElse(null));
        }
        return productRepository.findById(id)
            .map(product -> {
                if (product == null) {
                    throw BusinessException.notFound("Product");
                }
                return product;
            });
    }

    @Override
    public Future<List<JsonObject>> search(String keyword, String category) {
        if (!dbAvailable) {
            List<JsonObject> products = getDemoProducts().stream()
                .filter(p -> "active".equals(p.getString("status")))
                .toList();
            if (keyword != null && !keyword.isEmpty()) {
                String lower = keyword.toLowerCase();
                products = products.stream()
                    .filter(p -> p.getString("name", "").toLowerCase().contains(lower))
                    .toList();
            }
            if (category != null && !category.isEmpty()) {
                products = products.stream()
                    .filter(p -> category.equalsIgnoreCase(p.getString("category", "")))
                    .toList();
            }
            return Future.succeededFuture(products);
        }
        return productRepository.search(keyword, category);
    }

    @Override
    public Future<JsonObject> create(JsonObject product) {
        if (product == null) {
            return Future.failedFuture(BusinessException.badRequest("Request body is required"));
        }
        String name = product.getString("name");
        if (name == null || name.trim().isEmpty()) {
            return Future.failedFuture(BusinessException.badRequest("Product name is required"));
        }
        Double price = product.getDouble("price");
        if (price == null || price <= 0) {
            return Future.failedFuture(BusinessException.badRequest("Valid price is required"));
        }

        if (!dbAvailable) {
            JsonObject newProduct = product.copy();
            newProduct.put("id", System.currentTimeMillis());
            return Future.succeededFuture(newProduct);
        }

        return productRepository.existsByName(name)
            .compose(exists -> {
                if (exists) {
                    return Future.<JsonObject>failedFuture(
                        BusinessException.conflict("Product name already exists"));
                }
                return productRepository.create(product)
                    .compose(created -> audit.log(
                            AuditAction.AUDIT_CREATE, "products",
                            String.valueOf(created.getLong("id")),
                            null, created)
                        .map(created));
            });
    }

    @Override
    public Future<JsonObject> update(Long id, JsonObject product) {
        if (!dbAvailable) {
            return Future.succeededFuture(product.copy().put("id", id));
        }
        return productRepository.findById(id)
            .compose(existing -> {
                if (existing == null) {
                    return Future.<JsonObject>failedFuture(BusinessException.notFound("Product"));
                }
                return productRepository.update(id, product)
                    .compose(updated -> audit.log(
                            AuditAction.AUDIT_UPDATE, "products",
                            String.valueOf(id),
                            existing, updated)
                        .map(updated));
            })
            .map(updated -> {
                if (updated == null) {
                    throw BusinessException.notFound("Product");
                }
                return updated;
            });
    }

    @Override
    public Future<Void> delete(Long id) {
        if (!dbAvailable) {
            return Future.succeededFuture();
        }
        return productRepository.findById(id)
            .compose(existing -> {
                if (existing == null) {
                    return Future.<Void>failedFuture(BusinessException.notFound("Product"));
                }
                return productRepository.delete(id)
                    .compose(v -> audit.log(
                            AuditAction.AUDIT_DELETE, "products",
                            String.valueOf(id),
                            existing, null)
                        .mapEmpty());
            });
    }

    // ================================================================
    // BATCH OPERATIONS
    // ================================================================

    private static final int MAX_BATCH_SIZE = 100;

    @Override
    public Future<JsonObject> batchCreate(List<JsonObject> products) {
        if (products == null || products.isEmpty()) {
            return Future.failedFuture(BusinessException.badRequest("Request body must be a non-empty array"));
        }
        if (products.size() > MAX_BATCH_SIZE) {
            return Future.failedFuture(BusinessException.badRequest("Batch size exceeds maximum of " + MAX_BATCH_SIZE));
        }
        // Validate each product
        for (int i = 0; i < products.size(); i++) {
            JsonObject p = products.get(i);
            if (p.getString("name") == null || p.getString("name").trim().isEmpty()) {
                return Future.failedFuture(BusinessException.badRequest("Item[" + i + "]: name is required"));
            }
            if (p.getDouble("price") == null || p.getDouble("price") <= 0) {
                return Future.failedFuture(BusinessException.badRequest("Item[" + i + "]: valid price is required"));
            }
        }
        if (!dbAvailable) {
            // Demo mode: return simulated results
            List<JsonObject> created = products.stream().map(p -> {
                JsonObject copy = p.copy();
                copy.put("id", System.currentTimeMillis() + products.indexOf(p));
                return copy;
            }).toList();
            return Future.succeededFuture(new JsonObject()
                .put("created", created.size()).put("failed", 0)
                .put("items", created));
        }
        return productRepository.createBatch(products)
            .map(created -> new JsonObject()
                .put("created", created.size()).put("failed", 0)
                .put("items", created));
    }

    @Override
    public Future<JsonObject> batchUpdate(List<JsonObject> products) {
        if (products == null || products.isEmpty()) {
            return Future.failedFuture(BusinessException.badRequest("Request body must be a non-empty array"));
        }
        if (products.size() > MAX_BATCH_SIZE) {
            return Future.failedFuture(BusinessException.badRequest("Batch size exceeds maximum of " + MAX_BATCH_SIZE));
        }
        // Validate each item has id
        for (int i = 0; i < products.size(); i++) {
            if (products.get(i).getLong("id") == null) {
                return Future.failedFuture(BusinessException.badRequest("Item[" + i + "]: id is required"));
            }
        }
        if (!dbAvailable) {
            return Future.succeededFuture(new JsonObject()
                .put("updated", products.size()).put("failed", 0)
                .put("items", products));
        }
        // Sequential update (each returns updated row)
        List<JsonObject> updated = new java.util.ArrayList<>();
        int[] failed = {0};
        List<JsonObject> failedItems = new java.util.ArrayList<>();
        Future<JsonObject> result = Future.succeededFuture();
        for (JsonObject p : products) {
            result = result.compose(v -> productRepository.update(p.getLong("id"), p)
                .onSuccess(row -> updated.add(row))
                .onFailure(err -> { failed[0]++; failedItems.add(p); })
                .mapEmpty());
        }
        return result.map(v -> new JsonObject()
            .put("updated", updated.size()).put("failed", failed[0])
            .put("items", updated)
            .put("failedItems", failedItems));
    }

    @Override
    public Future<JsonObject> batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Future.failedFuture(BusinessException.badRequest("ids must be a non-empty array"));
        }
        if (ids.size() > MAX_BATCH_SIZE) {
            return Future.failedFuture(BusinessException.badRequest("Batch size exceeds maximum of " + MAX_BATCH_SIZE));
        }
        if (!dbAvailable) {
            return Future.succeededFuture(new JsonObject()
                .put("deleted", ids.size()).put("failed", 0));
        }
        return productRepository.deleteByIds(ids)
            .map(deleted -> new JsonObject()
                .put("deleted", deleted).put("failed", ids.size() - deleted));
    }

    // ================================================================
    // PAGINATION
    // ================================================================

    @Override
    public Future<PageResult<JsonObject>> findPaginated(int page, int size) {
        if (!dbAvailable) {
            List<JsonObject> demo = getDemoProducts();
            int start = (page - 1) * size;
            int end = Math.min(start + size, demo.size());
            List<JsonObject> pageData = start < demo.size() ? demo.subList(start, end) : List.of();
            return Future.succeededFuture(new PageResult<>(pageData, demo.size(), page, size));
        }
        
        return productRepository.count()
            .compose(total -> productRepository.findPaginated(page, size)
                .map(list -> new PageResult<>(list, total, page, size)));
    }

    @Override
    public Future<PageResult<JsonObject>> searchPaginated(String keyword, String category, int page, int size) {
        if (!dbAvailable) {
            List<JsonObject> products = getDemoProducts().stream()
                .filter(p -> "active".equals(p.getString("status")))
                .toList();
            if (keyword != null && !keyword.isEmpty()) {
                String lower = keyword.toLowerCase();
                products = products.stream()
                    .filter(p -> p.getString("name", "").toLowerCase().contains(lower))
                    .toList();
            }
            if (category != null && !category.isEmpty()) {
                products = products.stream()
                    .filter(p -> category.equalsIgnoreCase(p.getString("category", "")))
                    .toList();
            }
            int start = (page - 1) * size;
            int end = Math.min(start + size, products.size());
            List<JsonObject> pageData = start < products.size() ? products.subList(start, end) : List.of();
            return Future.succeededFuture(new PageResult<>(pageData, products.size(), page, size));
        }

        return productRepository.searchCount(keyword, category)
            .compose(total -> productRepository.searchPaginated(keyword, category, page, size)
                .map(list -> new PageResult<>(list, total, page, size)));
    }

    // ================================================================
    // ADDITIONAL METHODS
    // ================================================================

    public Future<List<JsonObject>> findByCategory(String category) {
        if (!dbAvailable) {
            return Future.succeededFuture(getDemoProducts().stream()
                .filter(p -> category.equalsIgnoreCase(p.getString("category", "")))
                .toList());
        }
        return productRepository.findByCategory(category);
    }

    // ================================================================
    // DEMO DATA (when DB not available)
    // ================================================================

    private List<JsonObject> getDemoProducts() {
        return List.of(
            new JsonObject()
                .put("id", 1L).put("name", "iPhone 15").put("category", "Electronics")
                .put("price", 799.99).put("stock", 100).put("status", "active")
                .put("description", "Apple smartphone").put("_demo", true),
            new JsonObject()
                .put("id", 2L).put("name", "MacBook Pro").put("category", "Electronics")
                .put("price", 1999.99).put("stock", 50).put("status", "active")
                .put("description", "Apple laptop").put("_demo", true),
            new JsonObject()
                .put("id", 3L).put("name", "Coffee Maker").put("category", "Home")
                .put("price", 49.99).put("stock", 200).put("status", "active")
                .put("description", "Automatic coffee maker").put("_demo", true)
        );
    }
}
