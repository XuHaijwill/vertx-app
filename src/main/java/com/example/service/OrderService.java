package com.example.service;

import com.example.core.BusinessException;
import com.example.core.PageResult;
import com.example.db.DatabaseVerticle;
import com.example.db.TransactionContext;
import com.example.repository.InventoryTransactionRepository;
import com.example.repository.OrderRepository;
import com.example.repository.ProductRepository;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

/**
 * Order Service — demonstrates multi-table transaction patterns.
 *
 * <p>Transaction scenarios:
 *
 * <ol>
 *   <li><b>createOrder</b> — INSERT order + N items + deduct stock (3 tables, 1 transaction)
 *       <br>Steps: validate → lock user → insert order → insert items → deduct stock per item
 *   <li><b>cancelOrder</b> — lock order + restore stock + update status (3 tables, 1 transaction)
 *       <br>Steps: lock order FOR UPDATE → restore stock per item → update status
 *       <br><b>Fixed:</b> order lock moved INSIDE transaction (was TOCTOU — read outside tx)
 * </ol>
 *
 * <p>TOCTOU fix in cancelOrder:
 * Before: findById (outside tx) → check status → withTransaction { updateStatus }
 * After:  withTransaction { findByIdForUpdate → check status → updateStatus }
 *
 * <p>The latter is safe because FOR UPDATE holds a row lock for the whole transaction.
 */
public class OrderService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepo;
    private final ProductRepository productRepo;
    private final InventoryTransactionRepository invTxRepo;
    private final Vertx vertx;
    private final boolean dbAvailable;

    public OrderService(Vertx vertx) {
        this.vertx = vertx;
        this.orderRepo = new OrderRepository(vertx);
        this.productRepo = new ProductRepository(vertx);
        this.invTxRepo = new InventoryTransactionRepository(vertx);
        this.dbAvailable = DatabaseVerticle.getPool(vertx) != null;
    }

    // ================================================================
    // Transactional: Create Order (3 tables: orders + order_items + products)
    // ================================================================

    /**
     * Create an order with multiple items — all-or-nothing transaction.
     *
     * <p>Steps within ONE transaction:
     *   1. Lock user row (FOR UPDATE) — prevent concurrent order creation abuse
     *   2. INSERT orders row
     *   3. INSERT N order_items rows (sequential)
     *   4. Deduct stock for each product + record inventory ledger (sequential)
     *
     * <p>Any failure at any step → full rollback (no partial orders).
     */
    public Future<JsonObject> createOrder(JsonObject order) {
        if (!dbAvailable) {
            return Future.failedFuture(BusinessException.serverError("Database not available"));
        }

        Long userId = order.getLong("userId");
        JsonArray items = order.getJsonArray("items");
        String remark = order.getString("remark", "");

        if (userId == null) {
            return Future.failedFuture(BusinessException.badRequest("userId is required"));
        }
        if (items == null || items.isEmpty()) {
            return Future.failedFuture(BusinessException.badRequest("At least one item is required"));
        }

        // Pre-calculate total from request body
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < items.size(); i++) {
            JsonObject item = items.getJsonObject(i);
            BigDecimal price = BigDecimal.valueOf(item.getDouble("price", 0.0));
            int qty = item.getInteger("quantity", 1);
            if (qty <= 0) {
                return Future.failedFuture(
                    BusinessException.badRequest("Quantity must be > 0 for item " + (i + 1)));
            }
            total = total.add(price.multiply(BigDecimal.valueOf(qty)));
        }
        final BigDecimal orderTotal = total;
        final int itemCount = items.size();

        return DatabaseVerticle.withTransaction(vertx, tx ->
            // Step 1: Lock user row (prevent concurrent order abuse)
            findUserForUpdate(tx, userId)
                // Step 2: Insert order
                .compose(user -> orderRepo.insertOrder(tx, userId, orderTotal, remark))
                // Step 3: Insert items
                .compose(orderId -> insertItemsSequence(tx, orderId, items, 0)
                    .map(orderId))
                // Step 4: Deduct stock + record ledger per item
                .compose(orderId -> deductStockSequence(tx, orderId, items, 0)
                    .map(orderId))
                // Step 5: Build result
                .map(orderId -> new JsonObject()
                    .put("id", orderId)
                    .put("userId", userId)
                    .put("total", orderTotal)
                    .put("remark", remark)
                    .put("status", "pending")
                    .put("itemCount", itemCount)),
            60_000  // 60s timeout — items loop may take a while
        ).onSuccess(result -> LOG.info("[ORDER] Created id={}, userId={}, total={}, items={}",
            result.getLong("id"), userId, orderTotal, itemCount))
        .onFailure(err -> LOG.error("[ORDER] Create failed: {}", err.getMessage()));
    }

    // ================================================================
    // Transactional: Cancel Order (3 tables: orders + products + inventory_transactions)
    // ================================================================

    /**
     * Cancel an order — restore stock + update status in ONE transaction.
     *
     * <p>FIXED TOCTOU: both the status check and the update happen inside the
     * same transaction with FOR UPDATE lock. Concurrent cancel requests will
     * block on the lock; the second one will see the updated status and fail
     * gracefully (not proceed to double-restore stock).
     *
     * <p>Steps within ONE transaction:
     *   1. Lock order row FOR UPDATE → concurrent cancels are blocked here
     *   2. Validate status (pending/paid only — completed/cancelled already handled)
     *   3. Restore stock for each item + record inventory ledger
     *   4. Update order status to cancelled
     */
    public Future<JsonObject> cancelOrder(Long orderId) {
        if (!dbAvailable) {
            return Future.failedFuture(BusinessException.serverError("Database not available"));
        }

        return DatabaseVerticle.withTransaction(vertx, tx ->
            // Step 1: Lock order row (prevents TOCTOU race)
            orderRepo.findByIdForUpdate(tx, orderId)
                .compose(order -> {
                    // Step 2: Validate status INSIDE the lock
                    String status = order.getString("status");
                    if ("cancelled".equals(status)) {
                        return Future.failedFuture(
                            BusinessException.conflict("Order already cancelled"));
                    }
                    if ("completed".equals(status)) {
                        return Future.failedFuture(
                            BusinessException.badRequest("Cannot cancel completed order"));
                    }
                    if (!"pending".equals(status) && !"paid".equals(status)) {
                        return Future.failedFuture(
                            BusinessException.badRequest("Cannot cancel order in status: " + status));
                    }
                    return Future.succeededFuture(order);
                })
                // Step 3: Restore stock + record ledger
                .compose(order -> orderRepo.findItemsByOrderIdInTx(tx, orderId)
                    .compose(items -> restoreStockSequence(tx, orderId, items, 0)
                        .map(order)))
                // Step 4: Update status
                .compose(order -> orderRepo.updateStatusInTx(tx, orderId, "cancelled")
                    .map(order)),
            30_000  // 30s timeout — simpler than create, shorter timeout
        ).onSuccess(result -> LOG.info("[ORDER] Cancelled id={}", orderId))
        .onFailure(err -> LOG.error("[ORDER] Cancel failed: {}", err.getMessage()));
    }

    // ================================================================
    // Read-only queries (no transaction)
    // ================================================================

    public Future<List<JsonObject>> findAll() {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return orderRepo.findAll();
    }

    public Future<JsonObject> findById(Long id) {
        if (!dbAvailable) return Future.succeededFuture(null);
        return orderRepo.findById(id)
            .map(order -> {
                if (order == null) throw BusinessException.notFound("Order");
                return order;
            });
    }

    public Future<List<JsonObject>> findByUserId(Long userId) {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return orderRepo.findByUserId(userId);
    }

    public Future<PageResult<JsonObject>> findPaginated(int page, int size) {
        if (!dbAvailable) {
            return Future.succeededFuture(new PageResult<>(List.of(), 0, page, size));
        }
        return orderRepo.count()
            .compose(total -> orderRepo.findPaginated(page, size)
                .map(list -> new PageResult<>(list, total, page, size)));
    }

    // ================================================================
    // Private helpers
    // ================================================================

    private Future<Void> insertItemsSequence(TransactionContext tx, Long orderId,
                                             JsonArray items, int index) {
        if (index >= items.size()) return Future.succeededFuture();
        JsonObject item = items.getJsonObject(index);
        return orderRepo.insertItem(tx, orderId, item.getLong("productId"),
                item.getInteger("quantity", 1),
                BigDecimal.valueOf(item.getDouble("price", 0.0)))
            .compose(v -> insertItemsSequence(tx, orderId, items, index + 1));
    }

    /**
     * Deduct stock per item: lock product → deduct → record ledger.
     * Sequential to maintain audit order and stop at first stock failure.
     */
    private Future<Void> deductStockSequence(TransactionContext tx, Long orderId,
                                              JsonArray items, int index) {
        if (index >= items.size()) return Future.succeededFuture();
        JsonObject item = items.getJsonObject(index);
        Long productId = item.getLong("productId");
        int quantity = item.getInteger("quantity", 1);

        return productRepo.findByIdForUpdate(tx, productId)
            .compose(product -> {
                int before = product.getInteger("stock", 0);
                return productRepo.deductStock(tx, productId, quantity, orderId)
                    .compose(after -> invTxRepo.recordDeduction(tx, productId, orderId,
                        -quantity, before, after,
                        "Order " + orderId + ": deduct " + quantity + " unit(s)"))
                    .mapEmpty();
            })
            .compose(v -> deductStockSequence(tx, orderId, items, index + 1));
    }

    /**
     * Restore stock per item: lock product → restore → record ledger.
     */
    private Future<Void> restoreStockSequence(TransactionContext tx, Long orderId,
                                               JsonArray items, int index) {
        if (index >= items.size()) return Future.succeededFuture();
        JsonObject item = items.getJsonObject(index);
        Long productId = item.getLong("productId");
        int quantity = item.getInteger("quantity", 1);

        return productRepo.findByIdForUpdate(tx, productId)
            .compose(product -> {
                int before = product.getInteger("stock", 0);
                return productRepo.restoreStock(tx, productId, quantity, orderId)
                    .compose(after -> invTxRepo.recordRestoration(tx, productId, orderId,
                        quantity, before, after,
                        "Order " + orderId + " cancelled: restore " + quantity + " unit(s)"))
                    .mapEmpty();
            })
            .compose(v -> restoreStockSequence(tx, orderId, items, index + 1));
    }

    private Future<JsonObject> findUserForUpdate(TransactionContext tx, Long userId) {
        String sql = "SELECT * FROM users WHERE id = $1 FOR UPDATE";
        Tuple params = Tuple.tuple().addLong(userId);
        return DatabaseVerticle.queryOneInTx(tx.conn(), sql, params)
            .map(user -> {
                if (user == null) throw new RuntimeException("User not found: " + userId);
                return user;
            });
    }
}
