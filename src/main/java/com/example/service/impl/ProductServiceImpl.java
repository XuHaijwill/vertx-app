package com.example.service.impl;

import com.example.core.BusinessException;
import com.example.core.PageResult;
import com.example.db.AuditAction;

import com.example.repository.ProductRepository;
import com.example.service.ProductService;
import com.example.entity.Product;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Product Service Implementation */
public class ProductServiceImpl extends BaseServiceImpl<ProductRepository> implements ProductService {

    private static final Logger LOG = LoggerFactory.getLogger(ProductServiceImpl.class);
    public ProductServiceImpl(Vertx vertx) { super(vertx, ProductRepository::new); }

    // ── READ ────────────────────────────────────────────────────────────────

    @Override
    public Future<List<Product>> findAll() {
        if (!dbAvailable) return Future.succeededFuture(getDemoProducts());
        return repo.findAll();
    }

    @Override
    public Future<Product> findById(Long id) {
        if (!dbAvailable) {
            Product found = getDemoProducts().stream()
                .filter(p -> id.equals(p.getId())).findFirst().orElse(null);
            return Future.succeededFuture(found);
        }
        return repo.findById(id)
            .map(p -> {
                if (p == null) throw BusinessException.notFound("Product");
                return p;
            });
    }

    @Override
    public Future<List<Product>> search(String keyword, String category) {
        if (!dbAvailable) {
            List<Product> results = getDemoProducts().stream()
                .filter(p -> "active".equals(p.getStatus()))
                .filter(p -> keyword == null || keyword.isBlank()
                    || (p.getName() != null && p.getName().toLowerCase().contains(keyword.toLowerCase())))
                .filter(p -> category == null || category.isEmpty()
                    || category.equalsIgnoreCase(p.getCategory()))
                .toList();
            return Future.succeededFuture(results);
        }
        return repo.search(keyword, category);
    }

    @Override
    public Future<PageResult<Product>> findPaginated(int page, int size) {
        if (!dbAvailable) {
            List<Product> all = getDemoProducts();
            int start = (page - 1) * size;
            int end = Math.min(start + size, all.size());
            List<Product> pageData = start < all.size() ? all.subList(start, end) : List.of();
            return Future.succeededFuture(new PageResult<>(pageData, all.size(), page, size));
        }
        return repo.count()
            .compose(total -> repo.findPaginated(page, size)
                .map(list -> new PageResult<>(list, total, page, size)));
    }

    @Override
    public Future<PageResult<Product>> searchPaginated(String keyword, String category, int page, int size) {
        if (!dbAvailable) {
            List<Product> results = getDemoProducts().stream()
                .filter(p -> "active".equals(p.getStatus()))
                .filter(p -> keyword == null || keyword.isBlank()
                    || (p.getName() != null && p.getName().toLowerCase().contains(keyword.toLowerCase())))
                .filter(p -> category == null || category.isEmpty()
                    || category.equalsIgnoreCase(p.getCategory()))
                .toList();
            int start = (page - 1) * size;
            int end = Math.min(start + size, results.size());
            List<Product> pageData = start < results.size() ? results.subList(start, end) : List.of();
            return Future.succeededFuture(new PageResult<>(pageData, results.size(), page, size));
        }
        return repo.searchCount(keyword, category)
            .compose(total -> repo.searchPaginated(keyword, category, page, size)
                .map(list -> new PageResult<>(list, total, page, size)));
    }

    // ── WRITE ───────────────────────────────────────────────────────────────

    @Override
    public Future<Product> create(Product product) {
        if (product == null)
            return Future.failedFuture(BusinessException.badRequest("Request body is required"));
        if (product.getName() == null || product.getName().trim().isEmpty())
            return Future.failedFuture(BusinessException.badRequest("Product name is required"));
        if (product.getPrice() == null || product.getPrice().doubleValue() <= 0)
            return Future.failedFuture(BusinessException.badRequest("Valid price is required"));

        if (!dbAvailable) {
            Product created = Product.fromJson(product.toJson().put("id", System.currentTimeMillis()));
            return Future.succeededFuture(created);
        }
        return repo.existsByName(product.getName())
            .compose(exists -> {
                if (exists)
                    return Future.<Product>failedFuture(BusinessException.conflict("Product name already exists"));
                return repo.create(product)
                    .map(created -> {
                        audit.log(AuditAction.AUDIT_CREATE, "products", String.valueOf(created.getId()),
                            null, created.toJson());
                        return created;
                    });
            });
    }

    @Override
    public Future<Product> update(Long id, Product product) {
        if (!dbAvailable) {
            return Future.succeededFuture(Product.fromJson(product.toJson().put("id", id)));
        }
        return repo.findById(id)
            .compose(existing -> {
                if (existing == null)
                    return Future.<Product>failedFuture(BusinessException.notFound("Product"));
                return repo.update(id, product)
                    .map(updated -> {
                        if (updated == null) throw BusinessException.notFound("Product");
                        audit.log(AuditAction.AUDIT_UPDATE, "products", String.valueOf(id),
                            existing.toJson(), updated.toJson());
                        return updated;
                    });
            });
    }

    @Override
    public Future<Void> delete(Long id) {
        if (!dbAvailable) return Future.succeededFuture();
        return repo.findById(id)
            .compose(existing -> {
                if (existing == null)
                    return Future.<Void>failedFuture(BusinessException.notFound("Product"));
                return repo.delete(id)
                    .compose(v -> audit.log(AuditAction.AUDIT_DELETE, "products", String.valueOf(id),
                        existing.toJson(), null).mapEmpty());
            });
    }

    // ── BATCH ───────────────────────────────────────────────────────────────

    private static final int MAX_BATCH = 100;

    private void validateBatch(List<Product> items) {
        if (items == null || items.isEmpty())
            throw BusinessException.badRequest("Request body must be a non-empty array");
        if (items.size() > MAX_BATCH)
            throw BusinessException.badRequest("Batch size exceeds maximum of " + MAX_BATCH);
    }

    @Override
    public Future<List<Product>> batchCreate(List<Product> products) {
        validateBatch(products);
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            if (p.getName() == null || p.getName().trim().isEmpty())
                throw BusinessException.badRequest("Item[" + i + "]: name is required");
            if (p.getPrice() == null || p.getPrice().doubleValue() <= 0)
                throw BusinessException.badRequest("Item[" + i + "]: valid price is required");
        }
        if (!dbAvailable) {
            List<Product> created = new ArrayList<>();
            for (int i = 0; i < products.size(); i++) {
                created.add(Product.fromJson(products.get(i).toJson()
                    .put("id", System.currentTimeMillis() + i)));
            }
            return Future.succeededFuture(created);
        }
        return repo.createBatch(products);
    }

    @Override
    public Future<List<Product>> batchUpdate(List<Product> products) {
        validateBatch(products);
        for (int i = 0; i < products.size(); i++) {
            if (products.get(i).getId() == null)
                throw BusinessException.badRequest("Item[" + i + "]: id is required");
        }
        if (!dbAvailable) {
            return Future.succeededFuture(products.stream()
                .map(p -> Product.fromJson(p.toJson())).toList());
        }
        List<Product> updated = new ArrayList<>();
        int[] failed = {0};
        Future<Void> chain = Future.succeededFuture();
        for (Product p : products) {
            chain = chain.compose(v -> repo.update(p.getId(), p)
                .onSuccess(u -> { if (u != null) updated.add(u); })
                .onFailure(e -> failed[0]++).mapEmpty());
        }
        return chain.map(v -> updated);
    }

    @Override
    public Future<Integer> batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty())
            return Future.failedFuture(BusinessException.badRequest("ids must be a non-empty array"));
        if (ids.size() > MAX_BATCH)
            return Future.failedFuture(BusinessException.badRequest("Batch size exceeds maximum of " + MAX_BATCH));
        if (!dbAvailable) return Future.succeededFuture(ids.size());
        return repo.deleteByIds(ids);
    }

    // ── EXTRA ───────────────────────────────────────────────────────────────

    public Future<List<Product>> findByCategory(String category) {
        if (!dbAvailable) {
            return Future.succeededFuture(getDemoProducts().stream()
                .filter(p -> category.equalsIgnoreCase(p.getCategory())).toList());
        }
        return repo.findByCategory(category);
    }

    // ── DEMO DATA ───────────────────────────────────────────────────────────

    private List<Product> getDemoProducts() {
        return List.of(
            Product.fromJson(new JsonObject()
                .put("id", 1L).put("name", "iPhone 15").put("category", "Electronics")
                .put("price", 799.99).put("stock", 100).put("status", "active")
                .put("description", "Apple smartphone")),
            Product.fromJson(new JsonObject()
                .put("id", 2L).put("name", "MacBook Pro").put("category", "Electronics")
                .put("price", 1999.99).put("stock", 50).put("status", "active")
                .put("description", "Apple laptop")),
            Product.fromJson(new JsonObject()
                .put("id", 3L).put("name", "Coffee Maker").put("category", "Home")
                .put("price", 49.99).put("stock", 200).put("status", "active")
                .put("description", "Automatic coffee maker"))
        );
    }
}
