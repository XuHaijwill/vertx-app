package com.example.repository;

import com.example.db.DatabaseVerticle;
import com.example.db.TransactionContext;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;

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
 * <p>Transactional methods receive TransactionContext, enabling multi-Repo
 * transactions alongside OrderRepository, ProductRepository, and UserRepository.
 */
public class PaymentRepository {

    private final Vertx vertx;

    public PaymentRepository(Vertx vertx) {
        this.vertx = vertx;
    }

    // ================================================================
    // Pool-based queries (standalone — no transaction)
    // ================================================================

    /**
     * Find payment by ID (with order + user info).
     */
    public Future<JsonObject> findById(Long id) {
        String sql = "SELECT p.*, o.total as order_total, o.status as order_status, " +
            "u.name as user_name FROM payments p " +
            "LEFT JOIN orders o ON p.order_id = o.id " +
            "LEFT JOIN users u ON p.user_id = u.id WHERE p.id = $1";
        Tuple params = Tuple.tuple().addLong(id);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    /**
     * Find payment by order ID.
     */
    public Future<JsonObject> findByOrderId(Long orderId) {
        String sql = "SELECT p.* FROM payments p WHERE p.order_id = $1 ORDER BY p.id DESC LIMIT 1";
        Tuple params = Tuple.tuple().addLong(orderId);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    /**
     * Find payments by user ID.
     */
    public Future<List<JsonObject>> findByUserId(Long userId) {
        String sql = "SELECT p.*, o.total as order_total FROM payments p " +
            "LEFT JOIN orders o ON p.order_id = o.id WHERE p.user_id = $1 " +
            "ORDER BY p.created_at DESC";
        Tuple params = Tuple.tuple().addLong(userId);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Find payments by status.
     */
    public Future<List<JsonObject>> findByStatus(String status) {
        String sql = "SELECT p.*, o.total as order_total, u.name as user_name FROM payments p " +
            "LEFT JOIN orders o ON p.order_id = o.id " +
            "LEFT JOIN users u ON p.user_id = u.id WHERE p.status = $1 " +
            "ORDER BY p.created_at DESC";
        Tuple params = Tuple.tuple().addString(status);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    // ================================================================
    // Transaction-based (context-aware) methods
    // ================================================================

    /**
     * Insert a new payment record inside a transaction.
     *
     * <p>Idempotency: pass the existing paymentId returned on success so callers
     * can detect duplicate submissions.
     *
     * @param tx       active transaction context
     * @param orderId  order being paid
     * @param userId   payer
     * @param amount   payment amount
     * @param method   payment method (balance|card|alipay|wechat)
     * @param status   initial status (pending|completed|failed)
     * @return payment ID
     */
    public Future<Long> insertPayment(TransactionContext tx, Long orderId, Long userId,
                                       java.math.BigDecimal amount, String method, String status) {
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

    /**
     * Update payment status inside a transaction.
     *
     * <p>If status transitions to completed/failed, also records completed_at.
     */
    public Future<Void> updateStatus(TransactionContext tx, Long paymentId, String status) {
        tx.tick();
        String completedAt = ("completed".equals(status) || "failed".equals(status) || "refunded".equals(status))
            ? "CURRENT_TIMESTAMP" : "NULL";
        String sql = String.format(
            "UPDATE payments SET status = $1, completed_at = %s, updated_at = CURRENT_TIMESTAMP WHERE id = $2",
            completedAt);
        Tuple params = Tuple.tuple().addString(status).addLong(paymentId);
        return DatabaseVerticle.updateInTx(tx.conn(), sql, params).mapEmpty();
    }

    /**
     * Find payment by order ID inside a transaction (for idempotency check).
     */
    public Future<JsonObject> findByOrderIdInTx(TransactionContext tx, Long orderId) {
        tx.tick();
        String sql = "SELECT * FROM payments WHERE order_id = $1 ORDER BY id DESC LIMIT 1";
        Tuple params = Tuple.tuple().addLong(orderId);
        return DatabaseVerticle.queryOneInTx(tx.conn(), sql, params);
    }

    /**
     * List recent payments for a user inside a transaction.
     */
    public Future<List<JsonObject>> findByUserIdInTx(TransactionContext tx, Long userId, int limit) {
        tx.tick();
        String sql = "SELECT p.*, o.total as order_total FROM payments p " +
            "LEFT JOIN orders o ON p.order_id = o.id WHERE p.user_id = $1 " +
            "ORDER BY p.created_at DESC LIMIT $2";
        Tuple params = Tuple.tuple().addLong(userId).addInteger(limit);
        return DatabaseVerticle.queryListInTx(tx.conn(), sql, params);
    }
}
