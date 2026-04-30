package com.example.repository;

import com.example.db.DatabaseVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;

import java.util.List;

/**
 * Order Repository — demonstrates transaction-aware database operations.
 *
 * Two categories of methods:
 *   1. Pool-based (standalone) — use DatabaseVerticle.query(), no transaction
 *   2. Connection-based (transactional) — receive SqlConnection, used inside withTransaction()
 */
public class OrderRepository {

    private final Vertx vertx;

    public OrderRepository(Vertx vertx) {
        this.vertx = vertx;
    }

    // ================================================================
    // Pool-based queries (standalone, no transaction)
    // ================================================================

    /**
     * Find all orders with user info
     */
    public Future<List<JsonObject>> findAll() {
        String sql = "SELECT o.*, u.name as user_name FROM orders o " +
            "LEFT JOIN users u ON o.user_id = u.id ORDER BY o.id DESC";
        return DatabaseVerticle.query(vertx, sql)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Find order by ID with items
     */
    public Future<JsonObject> findById(Long id) {
        String sql = "SELECT o.*, u.name as user_name FROM orders o " +
            "LEFT JOIN users u ON o.user_id = u.id WHERE o.id = $1";
        Tuple params = Tuple.tuple().addLong(id);
        return DatabaseVerticle.query(vertx, sql, params)
            .compose(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                if (list.isEmpty()) return Future.succeededFuture(null);
                JsonObject order = list.get(0);
                return findItemsByOrderId(id)
                    .map(items -> order.put("items", items));
            });
    }

    /**
     * Find orders by user ID
     */
    public Future<List<JsonObject>> findByUserId(Long userId) {
        String sql = "SELECT o.*, u.name as user_name FROM orders o " +
            "LEFT JOIN users u ON o.user_id = u.id WHERE o.user_id = $1 ORDER BY o.id DESC";
        Tuple params = Tuple.tuple().addLong(userId);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Find orders by status
     */
    public Future<List<JsonObject>> findByStatus(String status) {
        String sql = "SELECT o.*, u.name as user_name FROM orders o " +
            "LEFT JOIN users u ON o.user_id = u.id WHERE o.status = $1 ORDER BY o.id DESC";
        Tuple params = Tuple.tuple().addString(status);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Count all orders
     */
    public Future<Long> count() {
        String sql = "SELECT COUNT(*) as count FROM orders";
        return DatabaseVerticle.query(vertx, sql)
            .map(rows -> rows.iterator().next().getLong("count"));
    }

    // ================================================================
    // Pool-based item queries
    // ================================================================

    /**
     * Find order items by order ID
     */
    public Future<List<JsonObject>> findItemsByOrderId(Long orderId) {
        String sql = "SELECT oi.*, p.name as product_name FROM order_items oi " +
            "LEFT JOIN products p ON oi.product_id = p.id WHERE oi.order_id = $1";
        Tuple params = Tuple.tuple().addLong(orderId);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    // ================================================================
    // Connection-based (transactional) methods
    // These receive SqlConnection so they can participate in a transaction
    // ================================================================

    /**
     * Insert an order row within a transaction.
     *
     * @param conn Transaction-scoped SqlConnection
     * @return the generated order ID
     */
    public Future<Long> insertOrder(SqlConnection conn, Long userId, java.math.BigDecimal total, String remark) {
        String sql = "INSERT INTO orders (user_id, total, remark) VALUES ($1, $2, $3) RETURNING id";
        Tuple params = Tuple.tuple()
            .addLong(userId)
            .addBigDecimal(total)
            .addString(remark);
        return DatabaseVerticle.queryInTx(conn, sql, params)
            .map(rows -> rows.iterator().next().getLong("id"));
    }

    /**
     * Insert an order item row within a transaction.
     */
    public Future<Void> insertItem(SqlConnection conn, Long orderId, Long productId,
                                    int quantity, java.math.BigDecimal price) {
        String sql = "INSERT INTO order_items (order_id, product_id, quantity, price) VALUES ($1, $2, $3, $4)";
        Tuple params = Tuple.tuple()
            .addLong(orderId)
            .addLong(productId)
            .addInteger(quantity)
            .addBigDecimal(price);
        return DatabaseVerticle.queryInTx(conn, sql, params).mapEmpty();
    }

    /**
     * Deduct product stock within a transaction.
     * Fails if stock is insufficient (row count = 0).
     */
    public Future<Void> deductStock(SqlConnection conn, Long productId, int quantity) {
        String sql = "UPDATE products SET stock = stock - $1, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = $2 AND stock >= $1";
        Tuple params = Tuple.tuple().addInteger(quantity).addLong(productId);
        return DatabaseVerticle.queryInTx(conn, sql, params)
            .compose(rows -> {
                if (rows.rowCount() == 0) {
                    return Future.failedFuture(
                        new RuntimeException("Insufficient stock for product " + productId));
                }
                return Future.succeededFuture();
            });
    }

    /**
     * Update order status within a transaction.
     */
    public Future<Void> updateStatus(SqlConnection conn, Long orderId, String status) {
        String sql = "UPDATE orders SET status = $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2";
        Tuple params = Tuple.tuple().addString(status).addLong(orderId);
        return DatabaseVerticle.queryInTx(conn, sql, params).mapEmpty();
    }

    // ================================================================
    // Pagination
    // ================================================================

    public Future<List<JsonObject>> findPaginated(int page, int size) {
        int offset = (page - 1) * size;
        String sql = "SELECT o.*, u.name as user_name FROM orders o " +
            "LEFT JOIN users u ON o.user_id = u.id ORDER BY o.id DESC LIMIT $1 OFFSET $2";
        Tuple params = Tuple.tuple().addInteger(size).addInteger(offset);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }
}
