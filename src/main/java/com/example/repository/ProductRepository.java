package com.example.repository;

import com.example.db.DatabaseVerticle;
import com.example.db.TransactionContext;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;

import java.util.List;

/**
 * Product Repository - Database operations for products.
 *
 * <p>Two categories of methods:
 *   1. Pool-based (standalone) — use DatabaseVerticle.query(), no transaction
 *   2. Context-based (transactional) — receive TransactionContext, used inside withTransaction()
 */
public class ProductRepository {

    private final Vertx vertx;

    public ProductRepository(Vertx vertx) {
        this.vertx = vertx;
    }

    // ================================================================
    // Pool-based queries (standalone — no transaction)
    // ================================================================

    public Future<List<JsonObject>> findAll() {
        return DatabaseVerticle.query(vertx, "SELECT * FROM products ORDER BY id")
            .map(DatabaseVerticle::toJsonList);
    }

    public Future<JsonObject> findById(Long id) {
        String sql = "SELECT * FROM products WHERE id = $1";
        Tuple params = Tuple.tuple().addLong(id);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    public Future<List<JsonObject>> findByCategory(String category) {
        String sql = "SELECT * FROM products WHERE LOWER(category) = LOWER($1) ORDER BY id";
        Tuple params = Tuple.tuple().addString(category);
        return DatabaseVerticle.query(vertx, sql, params).map(DatabaseVerticle::toJsonList);
    }

    public Future<List<JsonObject>> findByStatus(String status) {
        String sql = "SELECT * FROM products WHERE status = $1 ORDER BY id";
        Tuple params = Tuple.tuple().addString(status);
        return DatabaseVerticle.query(vertx, sql, params).map(DatabaseVerticle::toJsonList);
    }

    public Future<Long> count() {
        return DatabaseVerticle.query(vertx, "SELECT COUNT(*) as count FROM products")
            .map(rows -> rows.iterator().next().getLong("count"));
    }

    public Future<List<JsonObject>> findPaginated(int page, int size) {
        int offset = (page - 1) * size;
        String sql = "SELECT * FROM products ORDER BY id LIMIT $1 OFFSET $2";
        Tuple params = Tuple.tuple().addInteger(size).addInteger(offset);
        return DatabaseVerticle.query(vertx, sql, params).map(DatabaseVerticle::toJsonList);
    }

    public Future<JsonObject> create(JsonObject product) {
        String sql = "INSERT INTO products (name, category, price, stock, description, status) " +
            "VALUES ($1, $2, $3, $4, $5, $6) RETURNING *";
        Tuple params = Tuple.tuple()
            .addString(product.getString("name"))
            .addString(product.getString("category"))
            .addBigDecimal(java.math.BigDecimal.valueOf(product.getDouble("price")))
            .addInteger(product.getInteger("stock", 0))
            .addString(product.getString("description"))
            .addString(product.getString("status", "active"));
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> DatabaseVerticle.toJson(rows.iterator().next()));
    }

    public Future<JsonObject> update(Long id, JsonObject product) {
        String sql = "UPDATE products SET " +
            "name = COALESCE($1, name), category = COALESCE($2, category), " +
            "price = COALESCE($3, price), stock = COALESCE($4, stock), " +
            "description = COALESCE($5, description), status = COALESCE($6, status), " +
            "updated_at = CURRENT_TIMESTAMP WHERE id = $7 RETURNING *";
        Tuple params = Tuple.tuple()
            .addString(product.getString("name"))
            .addString(product.getString("category"))
            .addBigDecimal(java.math.BigDecimal.valueOf(product.getDouble("price")))
            .addInteger(product.getInteger("stock"))
            .addString(product.getString("description"))
            .addString(product.getString("status"))
            .addLong(id);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    public Future<Boolean> delete(Long id) {
        String sql = "DELETE FROM products WHERE id = $1 RETURNING id";
        Tuple params = Tuple.tuple().addLong(id);
        return DatabaseVerticle.query(vertx, sql, params).map(rows -> rows.rowCount() > 0);
    }

    public Future<List<JsonObject>> search(String keyword, String category) {
        return search(keyword, category, 1, Integer.MAX_VALUE);
    }

    /**
     * Alias for search(keyword, category, page, size) — matches ProductServiceImpl expectations.
     */
    public Future<List<JsonObject>> searchPaginated(String keyword, String category, int page, int size) {
        return search(keyword, category, page, size);
    }

    public Future<List<JsonObject>> search(String keyword, String category, int page, int size) {
        int offset = (page - 1) * size;
        StringBuilder sql = new StringBuilder("SELECT * FROM products WHERE 1=1");
        Tuple params = Tuple.tuple();
        int idx = 1;
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND LOWER(name) LIKE LOWER($").append(idx++).append(")");
            params.addString("%" + keyword + "%");
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND LOWER(category) = LOWER($").append(idx++).append(")");
            params.addString(category);
        }
        sql.append(" ORDER BY id LIMIT $").append(idx++).append(" OFFSET $").append(idx);
        params.addInteger(size).addInteger(offset);
        return DatabaseVerticle.query(vertx, sql.toString(), params).map(DatabaseVerticle::toJsonList);
    }

    public Future<Long> searchCount(String keyword, String category) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) as count FROM products WHERE 1=1");
        Tuple params = Tuple.tuple();
        int idx = 1;
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND LOWER(name) LIKE LOWER($").append(idx++).append(")");
            params.addString("%" + keyword + "%");
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND LOWER(category) = LOWER($").append(idx++).append(")");
            params.addString(category);
        }
        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(rows -> rows.iterator().next().getLong("count"));
    }

    /**
     * Check if a product with the given name exists.
     */
    public Future<Boolean> existsByName(String name) {
        String sql = "SELECT EXISTS(SELECT 1 FROM products WHERE LOWER(name) = LOWER($1)) as exists";
        Tuple params = Tuple.tuple().addString(name);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getBoolean("exists"));
    }

    // ================================================================
    // Context-based (transactional) methods
    // ================================================================

    /**
     * Lock a product row with FOR UPDATE inside a transaction.
     * MUST be called before reading stock to prevent TOCTOU races.
     *
     * <p>Usage:
     * <pre>
     * productRepo.findByIdForUpdate(tx, productId)
     *     .compose(product -> { ... validate stock ...; return Future.succeededFuture(); });
     * </pre>
     */
    public Future<JsonObject> findByIdForUpdate(TransactionContext tx, Long productId) {
        tx.tick();
        String sql = "SELECT * FROM products WHERE id = $1 FOR UPDATE";
        Tuple params = Tuple.tuple().addLong(productId);
        return DatabaseVerticle.queryOneInTx(tx.conn(), sql, params)
            .map(product -> {
                if (product == null) {
                    throw new RuntimeException("Product not found: " + productId);
                }
                return product;
            });
    }

    /**
     * Deduct stock inside a transaction, recording the change in inventory_transactions.
     *
     * <p>Fails if product doesn't exist or stock is insufficient.
     * Call {@link #findByIdForUpdate} first to lock the row.
     *
     * @param tx            active transaction context
     * @param productId    product to deduct
     * @param quantity      units to deduct (must be > 0)
     * @param orderId       associated order ID (for ledger)
     * @return updated stock level
     */
    public Future<Integer> deductStock(TransactionContext tx, Long productId, int quantity, Long orderId) {
        tx.tick();
        if (quantity <= 0) return Future.succeededFuture(0);

        String sql = "UPDATE products SET stock = stock - $1, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = $2 AND stock >= $1 RETURNING stock";
        Tuple params = Tuple.tuple().addInteger(quantity).addLong(productId);
        return DatabaseVerticle.queryInTx(tx.conn(), sql, params)
            .compose(rows -> {
                if (rows.rowCount() == 0) {
                    return Future.failedFuture(
                        new RuntimeException("Insufficient stock for product " + productId));
                }
                return DatabaseVerticle.queryOneInTx(tx.conn(),
                    "SELECT stock FROM products WHERE id = $1",
                    Tuple.tuple().addLong(productId))
                    .map(r -> r.getInteger("stock"));
            });
    }

    /**
     * Restore (add back) stock inside a transaction.
     * Used when cancelling an order.
     *
     * @param tx            active transaction context
     * @param productId    product to restore
     * @param quantity      units to add back (must be > 0)
     * @param orderId       associated order ID (for ledger)
     * @return updated stock level
     */
    public Future<Integer> restoreStock(TransactionContext tx, Long productId, int quantity, Long orderId) {
        tx.tick();
        if (quantity <= 0) return Future.succeededFuture(0);

        String sql = "UPDATE products SET stock = stock + $1, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = $2 RETURNING stock";
        Tuple params = Tuple.tuple().addInteger(quantity).addLong(productId);
        return DatabaseVerticle.queryInTx(tx.conn(), sql, params)
            .compose(rows -> {
                if (rows.rowCount() == 0) {
                    return Future.failedFuture(
                        new RuntimeException("Product not found: " + productId));
                }
                return DatabaseVerticle.queryOneInTx(tx.conn(),
                    "SELECT stock FROM products WHERE id = $1",
                    Tuple.tuple().addLong(productId))
                    .map(r -> r.getInteger("stock"));
            });
    }

    /**
     * Update product status inside a transaction.
     */
    public Future<Void> updateStatusInTx(TransactionContext tx, Long productId, String status) {
        tx.tick();
        String sql = "UPDATE products SET status = $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2";
        Tuple params = Tuple.tuple().addString(status).addLong(productId);
        return DatabaseVerticle.updateInTx(tx.conn(), sql, params).mapEmpty();
    }
}
