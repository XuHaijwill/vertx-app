package com.example.repository;

import com.example.db.DatabaseVerticle;
import com.example.db.TransactionContext;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Order Repository — CRUD and transaction-aware operations for orders.
 *
 * <p>Two categories of methods:
 *   1. Pool-based (standalone) — use DatabaseVerticle.query(), no transaction
 *   2. Context-based (transactional) — receive TransactionContext, used inside withTransaction()
 *
 * <p>Critical: always use {@code findByIdForUpdate} before mutating an order's status
 * inside a transaction, to prevent concurrent modifications (TOCTOU race condition).
 */
public class OrderRepository {

    private final Vertx vertx;

    public OrderRepository(Vertx vertx) {
        this.vertx = vertx;
    }

    // ================================================================
    // Pool-based queries (standalone — no transaction)
    // ================================================================

    public Future<List<JsonObject>> findAll() {
        String sql = "SELECT o.*, u.name as user_name FROM orders o " +
            "LEFT JOIN users u ON o.user_id = u.id ORDER BY o.id DESC";
        return DatabaseVerticle.query(vertx, sql).map(DatabaseVerticle::toJsonList);
    }

    /**
     * Find order by ID with its items attached.
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
                return findItemsByOrderId(id).map(items -> order.put("items", items));
            });
    }

    public Future<List<JsonObject>> findByUserId(Long userId) {
        String sql = "SELECT o.*, u.name as user_name FROM orders o " +
            "LEFT JOIN users u ON o.user_id = u.id WHERE o.user_id = $1 ORDER BY o.id DESC";
        Tuple params = Tuple.tuple().addLong(userId);
        return DatabaseVerticle.query(vertx, sql, params).map(DatabaseVerticle::toJsonList);
    }

    public Future<List<JsonObject>> findByStatus(String status) {
        String sql = "SELECT o.*, u.name as user_name FROM orders o " +
            "LEFT JOIN users u ON o.user_id = u.id WHERE o.status = $1 ORDER BY o.id DESC";
        Tuple params = Tuple.tuple().addString(status);
        return DatabaseVerticle.query(vertx, sql, params).map(DatabaseVerticle::toJsonList);
    }

    public Future<Long> count() {
        return DatabaseVerticle.query(vertx, "SELECT COUNT(*) as count FROM orders")
            .map(rows -> rows.iterator().next().getLong("count"));
    }

    public Future<List<JsonObject>> findItemsByOrderId(Long orderId) {
        String sql = "SELECT oi.*, p.name as product_name FROM order_items oi " +
            "LEFT JOIN products p ON oi.product_id = p.id WHERE oi.order_id = $1";
        Tuple params = Tuple.tuple().addLong(orderId);
        return DatabaseVerticle.query(vertx, sql, params).map(DatabaseVerticle::toJsonList);
    }

    public Future<List<JsonObject>> findPaginated(int page, int size) {
        int offset = (page - 1) * size;
        String sql = "SELECT o.*, u.name as user_name FROM orders o " +
            "LEFT JOIN users u ON o.user_id = u.id ORDER BY o.id DESC LIMIT $1 OFFSET $2";
        Tuple params = Tuple.tuple().addInteger(size).addInteger(offset);
        return DatabaseVerticle.query(vertx, sql, params).map(DatabaseVerticle::toJsonList);
    }

    // ================================================================
    // Context-based (transactional) methods
    // ================================================================

    /**
     * Lock an order row with FOR UPDATE inside a transaction.
     *
     * <p>CRITICAL: Always call this BEFORE checking or modifying order status
     * in a multi-step transaction. Without this, concurrent requests can both
     * read the same status and both proceed, causing duplicate state transitions
     * (TOCTOU race condition).
     *
     * <p>Example:
     * <pre>
     * return orderRepo.findByIdForUpdate(tx, orderId)
     *     .compose(order -> {
     *         if (!"pending".equals(order.getString("status")))
     *             return Future.failedFuture(BusinessException.conflict("..."));
     *         return orderRepo.updateStatusInTx(tx, orderId, "paid");
     *     });
     * </pre>
     */
    public Future<JsonObject> findByIdForUpdate(TransactionContext tx, Long orderId) {
        tx.tick();
        String sql = "SELECT o.*, u.name as user_name FROM orders o " +
            "LEFT JOIN users u ON o.user_id = u.id WHERE o.id = $1 FOR UPDATE";
        Tuple params = Tuple.tuple().addLong(orderId);
        return DatabaseVerticle.queryOneInTx(tx.conn(), sql, params)
            .map(order -> {
                if (order == null) {
                    throw new RuntimeException("Order not found: " + orderId);
                }
                return order;
            });
    }

    /**
     * Find order items inside a transaction.
     */
    public Future<JsonArray> findItemsByOrderIdInTx(TransactionContext tx, Long orderId) {
        tx.tick();
        String sql = "SELECT oi.*, p.name as product_name FROM order_items oi " +
            "LEFT JOIN products p ON oi.product_id = p.id WHERE oi.order_id = $1";
        Tuple params = Tuple.tuple().addLong(orderId);
        return DatabaseVerticle.queryListInTx(tx.conn(), sql, params)
            .map(list -> {
                JsonArray arr = new JsonArray();
                for (JsonObject item : list) arr.add(item);
                return arr;
            });
    }

    /**
     * Insert an order row inside a transaction.
     *
     * @return the generated order ID
     */
    public Future<Long> insertOrder(TransactionContext tx, Long userId,
                                     java.math.BigDecimal total, String remark) {
        tx.tick();
        String sql = "INSERT INTO orders (user_id, total, remark) VALUES ($1, $2, $3) RETURNING id";
        Tuple params = Tuple.tuple().addLong(userId).addBigDecimal(total).addString(remark);
        return DatabaseVerticle.queryInTx(tx.conn(), sql, params)
            .map(rows -> rows.iterator().next().getLong("id"));
    }

    /**
     * Insert an order item row inside a transaction.
     */
    public Future<Void> insertItem(TransactionContext tx, Long orderId, Long productId,
                                    int quantity, java.math.BigDecimal price) {
        tx.tick();
        String sql = "INSERT INTO order_items (order_id, product_id, quantity, price) VALUES ($1, $2, $3, $4)";
        Tuple params = Tuple.tuple().addLong(orderId).addLong(productId)
            .addInteger(quantity).addBigDecimal(price);
        return DatabaseVerticle.queryInTx(tx.conn(), sql, params).mapEmpty();
    }

    /**
     * Update order status inside a transaction.
     * Always call {@link #findByIdForUpdate} first to lock the row.
     */
    public Future<Void> updateStatusInTx(TransactionContext tx, Long orderId, String status) {
        tx.tick();
        String sql = "UPDATE orders SET status = $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2";
        Tuple params = Tuple.tuple().addString(status).addLong(orderId);
        return DatabaseVerticle.updateInTx(tx.conn(), sql, params).mapEmpty();
    }
}
