package com.example.repository;

import com.example.db.DatabaseVerticle;
import com.example.db.TransactionContext;
import com.example.db.TxContextHolder;
import com.example.entity.Payment;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Payment Repository — manages payment records with transaction support.
 *
 * <p>Payment lifecycle:
 *   pending → completed (on success)
 *   pending → failed (on balance/user error)
 *   completed → refunded (on refund)
 *
 * <p>All methods support auto-routing via {@link TxContextHolder#current()}.
 * Within a {@code @Transactional} service method, no explicit {@code TransactionContext}
 * parameter is needed.
 */
public class PaymentRepository {

    private final Vertx vertx;

    public PaymentRepository(Vertx vertx) {
        this.vertx = vertx;
    }

    // ===== Entity Mapping =====
    private Payment toPayment(JsonObject json) {
        if (json == null) return null;
        Payment p = new Payment();
        p.setId((Long) json.getValue("id"));
        p.setOrderId((Long) json.getValue("order_id"));
        p.setUserId((Long) json.getValue("user_id"));
        Object amountObj = json.getValue("amount");
        p.setAmount(amountObj != null ? new BigDecimal(amountObj.toString()) : null);
        p.setMethod(json.getString("method"));
        p.setStatus(json.getString("status"));
        p.setTransactionNo(json.getString("transaction_no"));
        p.setRemark(json.getString("remark"));
        Object completedObj = json.getValue("completed_at");
        if (completedObj != null) {
            p.setCompletedAt(completedObj instanceof LocalDateTime ? (LocalDateTime) completedObj : LocalDateTime.parse(completedObj.toString()));
        }
        Object createdObj = json.getValue("created_at");
        if (createdObj != null) {
            p.setCreatedAt(createdObj instanceof LocalDateTime ? (LocalDateTime) createdObj : LocalDateTime.parse(createdObj.toString()));
        }
        Object updatedObj = json.getValue("updated_at");
        if (updatedObj != null) {
            p.setUpdatedAt(updatedObj instanceof LocalDateTime ? (LocalDateTime) updatedObj : LocalDateTime.parse(updatedObj.toString()));
        }
        // JOIN fields
        Object orderTotalObj = json.getValue("order_total");
        p.setOrderTotal(orderTotalObj != null ? new BigDecimal(orderTotalObj.toString()) : null);
        p.setOrderStatus(json.getString("order_status"));
        p.setUserName(json.getString("user_name"));
        return p;
    }

    private List<Payment> toPaymentList(List<JsonObject> jsonList) {
        if (jsonList == null) return null;
        return jsonList.stream().map(this::toPayment).collect(java.util.stream.Collectors.toList());
    }

    // ================================================================
    // Pool-based queries (standalone — no transaction)
    // ================================================================

    public Future<Payment> findById(Long id) {
        String sql = "SELECT p.*, o.total as order_total, o.status as order_status, " +
            "u.name as user_name FROM payments p " +
            "LEFT JOIN orders o ON p.order_id = o.id " +
            "LEFT JOIN users u ON p.user_id = u.id WHERE p.id = $1";
        Tuple params = Tuple.tuple().addLong(id);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                return list.isEmpty() ? null : toPayment(list.get(0));
            });
    }

    /**
     * Find payment by order ID (non-tx version).
     */
    public Future<Payment> findByOrderId(Long orderId) {
        String sql = "SELECT p.* FROM payments p WHERE p.order_id = $1 ORDER BY p.id DESC LIMIT 1";
        Tuple params = Tuple.tuple().addLong(orderId);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                return list.isEmpty() ? null : toPayment(list.get(0));
            });
    }

    public Future<List<Payment>> findByUserId(Long userId) {
        String sql = "SELECT p.*, o.total as order_total FROM payments p " +
            "LEFT JOIN orders o ON p.order_id = o.id WHERE p.user_id = $1 " +
            "ORDER BY p.created_at DESC";
        Tuple params = Tuple.tuple().addLong(userId);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> toPaymentList(DatabaseVerticle.toJsonList(rows)));
    }

    public Future<List<Payment>> findByStatus(String status) {
        String sql = "SELECT p.*, o.total as order_total, u.name as user_name FROM payments p " +
            "LEFT JOIN orders o ON p.order_id = o.id " +
            "LEFT JOIN users u ON p.user_id = u.id WHERE p.status = $1 " +
            "ORDER BY p.created_at DESC";
        Tuple params = Tuple.tuple().addString(status);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> toPaymentList(DatabaseVerticle.toJsonList(rows)));
    }

    // ================================================================
    // Auto-route variants (declarative-transaction — preferred entry points)
    // ================================================================

    /**
     * Insert a new payment record — auto-detects active transaction.
     *
     * @see #insertPayment(TransactionContext, Long, Long, BigDecimal, String, String)
     */
    public Future<Long> insertPayment(Long orderId, Long userId,
                                      BigDecimal amount, String method, String status) {
        TransactionContext tx = TxContextHolder.current();
        if (tx != null) return insertPaymentInTx(tx, orderId, userId, amount, method, status);
        return DatabaseVerticle.withTransaction(vertx,
            txCtx -> insertPaymentInTx(txCtx, orderId, userId, amount, method, status),
            10_000);
    }

    /**
     * Update payment status — auto-detects active transaction.
     *
     * @see #updateStatus(TransactionContext, Long, String)
     */
    public Future<Void> updatePaymentStatus(Long paymentId, String status) {
        TransactionContext tx = TxContextHolder.current();
        if (tx != null) return updateStatusInTx(tx, paymentId, status);
        return DatabaseVerticle.withTransaction(vertx,
            txCtx -> updateStatusInTx(txCtx, paymentId, status),
            5_000);
    }

    /**
     * Find payment by order ID inside a transaction — auto-detects active transaction.
     * Use for idempotency checks within a payment flow.
     *
     * @see #findByOrderIdInTx(TransactionContext, Long)
     */
    public Future<Payment> findByOrderIdForTx(Long orderId) {
        TransactionContext tx = TxContextHolder.current();
        if (tx != null) return findByOrderIdInTx(tx, orderId);
        return DatabaseVerticle.withTransaction(vertx,
            txCtx -> findByOrderIdInTx(txCtx, orderId), 5_000);
    }

    /**
     * List recent payments for a user inside a transaction — auto-detects active transaction.
     *
     * @see #findByUserIdInTx(TransactionContext, Long, int)
     */
    public Future<List<Payment>> findByUserIdForTx(Long userId, int limit) {
        TransactionContext tx = TxContextHolder.current();
        if (tx != null) return findByUserIdInTx(tx, userId, limit);
        return DatabaseVerticle.withTransaction(vertx,
            txCtx -> findByUserIdInTx(txCtx, userId, limit), 5_000);
    }

    // ================================================================
    // Context-based (explicit tx) — kept for advanced use cases
    // ================================================================

    /**
     * Insert a new payment record — explicit transaction.
     *
     * @return payment ID
     */
    public Future<Long> insertPayment(TransactionContext tx, Long orderId, Long userId,
                                     BigDecimal amount, String method, String status) {
        return insertPaymentInTx(tx, orderId, userId, amount, method, status);
    }

    /**
     * Update payment status — explicit transaction.
     */
    public Future<Void> updateStatus(TransactionContext tx, Long paymentId, String status) {
        return updateStatusInTx(tx, paymentId, status);
    }

    /**
     * Find payment by order ID inside a transaction — explicit context.
     */
    public Future<Payment> findByOrderIdInTx(TransactionContext tx, Long orderId) {
        tx.tick();
        String sql = "SELECT * FROM payments WHERE order_id = $1 ORDER BY id DESC LIMIT 1";
        Tuple params = Tuple.tuple().addLong(orderId);
        return DatabaseVerticle.queryOneInTx(tx.conn(), sql, params)
            .map(this::toPayment);
    }

    /**
     * List recent payments for a user inside a transaction — explicit context.
     */
    public Future<List<Payment>> findByUserIdInTx(TransactionContext tx, Long userId, int limit) {
        tx.tick();
        String sql = "SELECT p.*, o.total as order_total FROM payments p " +
            "LEFT JOIN orders o ON p.order_id = o.id WHERE p.user_id = $1 " +
            "ORDER BY p.created_at DESC LIMIT $2";
        Tuple params = Tuple.tuple().addLong(userId).addInteger(limit);
        return DatabaseVerticle.queryListInTx(tx.conn(), sql, params)
            .map(list -> toPaymentList(list));
    }

    // ================================================================
    // Private internal implementations
    // ================================================================

    private Future<Long> insertPaymentInTx(TransactionContext tx, Long orderId, Long userId,
                                            BigDecimal amount, String method, String status) {
        tx.tick();
        String transactionNo = "TXN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        String sql = "INSERT INTO payments (order_id, user_id, amount, method, status, transaction_no) " +
            "VALUES ($1, $2, $3, $4, $5, $6) RETURNING id";
        Tuple params = Tuple.tuple()
            .addLong(orderId)
            .addLong(userId)
            .addBigDecimal(amount)
            .addString(method)
            .addString(status)
            .addString(transactionNo);
        return DatabaseVerticle.queryInTx(tx.conn(), sql, params)
            .map(rows -> rows.iterator().next().getLong("id"));
    }

    private Future<Void> updateStatusInTx(TransactionContext tx, Long paymentId, String status) {
        tx.tick();
        String completedAt = ("completed".equals(status) || "failed".equals(status) || "refunded".equals(status))
            ? "CURRENT_TIMESTAMP" : "NULL";
        String sql = String.format(
            "UPDATE payments SET status = $1, completed_at = %s, updated_at = CURRENT_TIMESTAMP WHERE id = $2",
            completedAt);
        Tuple params = Tuple.tuple().addString(status).addLong(paymentId);
        return DatabaseVerticle.updateInTx(tx.conn(), sql, params).mapEmpty();
    }
}
