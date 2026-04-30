package com.example.repository;

import com.example.db.DatabaseVerticle;
import com.example.db.TransactionContext;
import com.example.db.TxContextHolder;
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
 * <p>Three categories of methods:
 *   1. <b>Pool-based</b> (standalone) — no transaction, uses connection pool
 *   2. <b>Auto-route</b> (declarative-tx) — auto-detects {@link TxContextHolder#current()},
 *      preferred entry points for {@code @Transactional} service methods
 *   3. <b>Context-based</b> (explicit tx) — receive {@code TransactionContext} parameter,
 *      kept for advanced use cases that need explicit transaction control
 *
 * <p>Critical TOCTOU rule: always use {@code findByIdForUpdate} (either variant)
 * before checking or modifying order status inside a transaction.
 * The FOR UPDATE lock serialises concurrent modifiers.
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
    // Auto-route variants (declarative-transaction — preferred entry points)
    // These are the cleanest to call from @Transactional service methods.
    // ================================================================

    /**
     * Lock order row with FOR UPDATE — auto-detects active transaction.
     *
     * @see #findByIdForUpdate(TransactionContext, Long)
     */
    public Future<JsonObject> findByIdForUpdate(Long orderId) {
        TransactionContext tx = TxContextHolder.current();
        if (tx != null) return findByIdForUpdateInTx(tx, orderId);
        return DatabaseVerticle.withTransaction(vertx,
            txCtx -> findByIdForUpdateInTx(txCtx, orderId), 5_000);
    }

    /**
     * Find order items inside a transaction — auto-detects active transaction.
     *
     * @return JsonArray (same as the explicit tx version)
     * @see #findItemsByOrderIdInTx(TransactionContext, Long)
     */
    public Future<JsonArray> findItemsByOrderIdForTx(Long orderId) {
        TransactionContext tx = TxContextHolder.current();
        if (tx != null) return findItemsByOrderIdInTx(tx, orderId);
        return DatabaseVerticle.withTransaction(vertx,
            txCtx -> findItemsByOrderIdInTx(txCtx, orderId), 5_000);
    }

    /**
     * Insert an order row — auto-detects active transaction.
     *
     * @return the generated order ID
     * @see #insertOrder(TransactionContext, Long, java.math.BigDecimal, String)
     */
    public Future<Long> insertOrder(Long userId, java.math.BigDecimal total, String remark) {
        TransactionContext tx = TxContextHolder.current();
        if (tx != null) return insertOrderInTx(tx, userId, total, remark);
        return DatabaseVerticle.withTransaction(vertx,
            txCtx -> insertOrderInTx(txCtx, userId, total, remark), 5_000);
    }

    /**
     * Insert an order item row — auto-detects active transaction.
     *
     * @see #insertItem(TransactionContext, Long, Long, int, java.math.BigDecimal)
     */
    public Future<Void> insertItem(Long orderId, Long productId,
                                   int quantity, java.math.BigDecimal price) {
        TransactionContext tx = TxContextHolder.current();
        if (tx != null) return insertItemInTx(tx, orderId, productId, quantity, price);
        return DatabaseVerticle.withTransaction(vertx,
            txCtx -> insertItemInTx(txCtx, orderId, productId, quantity, price), 5_000);
    }

    /**
     * Update order status — auto-detects active transaction.
     * Always call {@link #findByIdForUpdate(Long)} first to lock the row.
     *
     * @see #updateStatusInTx(TransactionContext, Long, String)
     */
    public Future<Void> updateStatus(Long orderId, String status) {
        TransactionContext tx = TxContextHolder.current();
        if (tx != null) return updateStatusInTx(tx, orderId, status);
        return DatabaseVerticle.withTransaction(vertx,
            txCtx -> updateStatusInTx(txCtx, orderId, status), 5_000);
    }

    // ================================================================
    // Context-based (explicit tx) — kept for advanced explicit-control use cases
    // ================================================================

    /**
     * Lock an order row with FOR UPDATE — explicit transaction context.
     *
     * <p>CRITICAL: Always call this BEFORE checking or modifying order status.
     * Without FOR UPDATE, concurrent requests can both read the same status
     * and both proceed, causing duplicate state transitions (TOCTOU race).
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
        return findByIdForUpdateInTx(tx, orderId);
    }

    /**
     * Find order items inside a transaction — explicit context.
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
     * Insert an order row inside a transaction — explicit context.
     *
     * @return the generated order ID
     */
    public Future<Long> insertOrder(TransactionContext tx, Long userId,
                                     java.math.BigDecimal total, String remark) {
        return insertOrderInTx(tx, userId, total, remark);
    }

    /**
     * Insert an order item row inside a transaction — explicit context.
     */
    public Future<Void> insertItem(TransactionContext tx, Long orderId, Long productId,
                                   int quantity, java.math.BigDecimal price) {
        return insertItemInTx(tx, orderId, productId, quantity, price);
    }

    /**
     * Update order status inside a transaction — explicit context.
     * Always call {@link #findByIdForUpdate(TransactionContext, Long)} first to lock the row.
     */
    public Future<Void> updateStatusInTx(TransactionContext tx, Long orderId, String status) {
        tx.tick();
        String sql = "UPDATE orders SET status = $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2";
        Tuple params = Tuple.tuple().addString(status).addLong(orderId);
        return DatabaseVerticle.updateInTx(tx.conn(), sql, params).mapEmpty();
    }

    // ================================================================
    // Private internal implementations (called by both auto-route and explicit)
    // ================================================================

    private Future<JsonObject> findByIdForUpdateInTx(TransactionContext tx, Long orderId) {
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

    private Future<Long> insertOrderInTx(TransactionContext tx, Long userId,
                                         java.math.BigDecimal total, String remark) {
        tx.tick();
        String sql = "INSERT INTO orders (user_id, total, remark) VALUES ($1, $2, $3) RETURNING id";
        Tuple params = Tuple.tuple().addLong(userId).addBigDecimal(total).addString(remark);
        return DatabaseVerticle.queryInTx(tx.conn(), sql, params)
            .map(rows -> rows.iterator().next().getLong("id"));
    }

    private Future<Void> insertItemInTx(TransactionContext tx, Long orderId, Long productId,
                                         int quantity, java.math.BigDecimal price) {
        tx.tick();
        String sql = "INSERT INTO order_items (order_id, product_id, quantity, price) VALUES ($1, $2, $3, $4)";
        Tuple params = Tuple.tuple().addLong(orderId).addLong(productId)
            .addInteger(quantity).addBigDecimal(price);
        return DatabaseVerticle.queryInTx(tx.conn(), sql, params).mapEmpty();
    }
}
