package com.example.service;

import com.example.core.BusinessException;
import com.example.core.PageResult;
import com.example.repository.ProductRepository;
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
    private final boolean dbAvailable;

    public ProductServiceImpl(Vertx vertx) {
        this.productRepository = new ProductRepository(vertx);
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
                return productRepository.create(product);
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
                return productRepository.update(id, product);
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
                return productRepository.delete(id).mapEmpty();
            });
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
