package com.example.repository;

import com.example.db.DatabaseVerticle;
import com.example.db.TransactionContext;
import com.example.db.TxContextHolder;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;

import java.util.List;

/**
 * InventoryTransaction Repository — stock change ledger.
 *
 * <p>Records every stock mutation (order_create / order_cancel / manual_adjust) for:
 * <ul>
 *   <li>Full traceability: who changed stock, when, by how much</li>
 *   <li>Audit trail: stock_before / stock_after for every change</li>
 *   <li>Anti-racing: used in conjunction with SELECT ... FOR UPDATE on products</li>
 * </ul>
 *
 * <p>All methods support auto-routing: if called inside a {@code @Transactional}
 * service method, the active transaction is detected via {@link TxContextHolder#current()};
 * if called outside a transaction, each call creates its own mini-transaction (5s timeout).
 */
public class InventoryTransactionRepository {

    private final Vertx vertx;

    public InventoryTransactionRepository(Vertx vertx) {
        this.vertx = vertx;
    }

    // ================================================================
    // Pool-based queries (standalone)
    // ================================================================

    /**
     * Find transaction history for a product.
     */
    public Future<List<JsonObject>> findByProductId(Long productId, int limit) {
        String sql = "SELECT it.*, p.name as product_name FROM inventory_transactions it " +
            "LEFT JOIN products p ON it.product_id = p.id " +
            "WHERE it.product_id = $1 ORDER BY it.created_at DESC LIMIT $2";
        Tuple params = Tuple.tuple().addLong(productId).addInteger(limit);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Find transaction history for an order.
     */
    public Future<List<JsonObject>> findByOrderId(Long orderId) {
        String sql = "SELECT it.*, p.name as product_name FROM inventory_transactions it " +
            "LEFT JOIN products p ON it.product_id = p.id " +
            "WHERE it.order_id = $1 ORDER BY it.created_at ASC";
        Tuple params = Tuple.tuple().addLong(orderId);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    // ================================================================
    // Auto-route variants (declarative-transaction — preferred entry points)
    // ================================================================

    /**
     * Record a stock deduction (order creation) — auto-detects active transaction.
     *
     * @see #recordDeduction(TransactionContext, Long, Long, int, int, int, String)
     */
    public Future<Long> recordDeduction(Long productId, Long orderId,
                                        int delta, int stockBefore, int stockAfter,
                                        String reason) {
        TransactionContext tx = TxContextHolder.current();
        if (tx != null) return recordDeductionInTx(tx, productId, orderId, delta, stockBefore, stockAfter, reason);
        return DatabaseVerticle.withTransaction(vertx,
            txCtx -> recordDeductionInTx(txCtx, productId, orderId, delta, stockBefore, stockAfter, reason),
            5_000);
    }

    /**
     * Record a stock restoration (order cancellation) — auto-detects active transaction.
     *
     * @see #recordRestoration(TransactionContext, Long, Long, int, int, int, String)
     */
    public Future<Long> recordRestoration(Long productId, Long orderId,
                                          int delta, int stockBefore, int stockAfter,
                                          String reason) {
        TransactionContext tx = TxContextHolder.current();
        if (tx != null) return recordRestorationInTx(tx, productId, orderId, delta, stockBefore, stockAfter, reason);
        return DatabaseVerticle.withTransaction(vertx,
            txCtx -> recordRestorationInTx(txCtx, productId, orderId, delta, stockBefore, stockAfter, reason),
            5_000);
    }

    /**
     * Record a manual stock adjustment — auto-detects active transaction.
     *
     * @see #recordAdjustment(TransactionContext, Long, int, int, int, String, Long)
     */
    public Future<Long> recordAdjustment(Long productId,
                                          int delta, int stockBefore, int stockAfter,
                                          String reason, Long operatorId) {
        TransactionContext tx = TxContextHolder.current();
        if (tx != null) return recordAdjustmentInTx(tx, productId, delta, stockBefore, stockAfter, reason, operatorId);
        return DatabaseVerticle.withTransaction(vertx,
            txCtx -> recordAdjustmentInTx(txCtx, productId, delta, stockBefore, stockAfter, reason, operatorId),
            5_000);
    }

    // ================================================================
    // Context-based (explicit tx) — kept for explicit transaction use cases
    // ================================================================

    /**
     * Record a stock deduction — explicit transaction.
     *
     * <p>Assumes the caller has already locked the product row with
     * {@code SELECT ... FOR UPDATE} to prevent TOCTOU races.
     *
     * @return the generated transaction ID
     */
    public Future<Long> recordDeduction(TransactionContext tx, Long productId, Long orderId,
                                         int delta, int stockBefore, int stockAfter, String reason) {
        return recordDeductionInTx(tx, productId, orderId, delta, stockBefore, stockAfter, reason);
    }

    /**
     * Record a stock restoration — explicit transaction.
     */
    public Future<Long> recordRestoration(TransactionContext tx, Long productId, Long orderId,
                                           int delta, int stockBefore, int stockAfter, String reason) {
        return recordRestorationInTx(tx, productId, orderId, delta, stockBefore, stockAfter, reason);
    }

    /**
     * Record a manual stock adjustment — explicit transaction.
     */
    public Future<Long> recordAdjustment(TransactionContext tx, Long productId,
                                          int delta, int stockBefore, int stockAfter,
                                          String reason, Long operatorId) {
        return recordAdjustmentInTx(tx, productId, delta, stockBefore, stockAfter, reason, operatorId);
    }

    // ================================================================
    // Private internal implementations
    // ================================================================

    private Future<Long> recordDeductionInTx(TransactionContext tx, Long productId, Long orderId,
                                             int delta, int stockBefore, int stockAfter,
                                             String reason) {
        tx.tick();
        return insertInTx(tx, productId, orderId, "order_create", delta, stockBefore, stockAfter, reason, null);
    }

    private Future<Long> recordRestorationInTx(TransactionContext tx, Long productId, Long orderId,
                                                int delta, int stockBefore, int stockAfter,
                                                String reason) {
        tx.tick();
        return insertInTx(tx, productId, orderId, "order_cancel", delta, stockBefore, stockAfter, reason, null);
    }

    private Future<Long> recordAdjustmentInTx(TransactionContext tx, Long productId,
                                               int delta, int stockBefore, int stockAfter,
                                               String reason, Long operatorId) {
        tx.tick();
        return insertInTx(tx, productId, null, "manual_adjust", delta, stockBefore, stockAfter, reason, operatorId);
    }

    private Future<Long> insertInTx(TransactionContext tx, Long productId, Long orderId,
                                    String type, int delta, int stockBefore, int stockAfter,
                                    String reason, Long operatorId) {
        String sql = "INSERT INTO inventory_transactions " +
            "(product_id, order_id, type, delta, stock_before, stock_after, reason, operator_id) " +
            "VALUES ($1, $2, $3, $4, $5, $6, $7, $8) RETURNING id";
        Tuple params = Tuple.tuple()
            .addLong(productId)
            .addLong(orderId)
            .addString(type)
            .addInteger(delta)
            .addInteger(stockBefore)
            .addInteger(stockAfter)
            .addString(reason)
            .addLong(operatorId);
        return DatabaseVerticle.queryInTx(tx.conn(), sql, params)
            .map(rows -> rows.iterator().next().getLong("id"));
    }
}
