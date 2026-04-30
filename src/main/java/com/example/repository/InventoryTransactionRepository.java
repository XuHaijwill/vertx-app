package com.example.repository;

import com.example.db.DatabaseVerticle;
import com.example.db.TransactionContext;
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
 * <p>All transaction-aware methods ensure the stock ledger stays consistent with
 * the actual product.stock field.
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
    // Transaction-based methods
    // ================================================================

    /**
     * Record a stock deduction (order creation) inside a transaction.
     *
     * <p>Assumes the caller has already locked the product row with
     * {@code SELECT ... FOR UPDATE} to prevent TOCTOU races.
     *
     * @param tx          active transaction context
     * @param productId   product being modified
     * @param orderId     associated order (nullable for manual adjustments)
     * @param delta       negative integer (e.g. -3 means 3 units sold)
     * @param stockBefore current stock BEFORE the change
     * @param stockAfter  expected stock AFTER the change
     * @param reason      human-readable reason
     * @return the generated transaction ID
     */
    public Future<Long> recordDeduction(TransactionContext tx, Long productId, Long orderId,
                                         int delta, int stockBefore, int stockAfter, String reason) {
        tx.tick();
        return insert(tx, productId, orderId, "order_create", delta, stockBefore, stockAfter, reason, null);
    }

    /**
     * Record a stock restoration (order cancellation) inside a transaction.
     */
    public Future<Long> recordRestoration(TransactionContext tx, Long productId, Long orderId,
                                           int delta, int stockBefore, int stockAfter, String reason) {
        tx.tick();
        return insert(tx, productId, orderId, "order_cancel", delta, stockBefore, stockAfter, reason, null);
    }

    /**
     * Record a manual stock adjustment (admin operation) inside a transaction.
     */
    public Future<Long> recordAdjustment(TransactionContext tx, Long productId,
                                          int delta, int stockBefore, int stockAfter,
                                          String reason, Long operatorId) {
        tx.tick();
        return insert(tx, productId, null, "manual_adjust", delta, stockBefore, stockAfter, reason, operatorId);
    }

    private Future<Long> insert(TransactionContext tx, Long productId, Long orderId,
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
