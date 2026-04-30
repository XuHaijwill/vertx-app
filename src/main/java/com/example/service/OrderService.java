package com.example.service;

import com.example.core.BusinessException;
import com.example.core.PageResult;
import com.example.db.DatabaseVerticle;
import com.example.db.Transactional;
import com.example.db.TransactionTemplate;
import com.example.db.TransactionContext;
import com.example.repository.InventoryTransactionRepository;
import com.example.repository.OrderRepository;
import com.example.repository.ProductRepository;
import com.example.repository.UserRepository;
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
 * Order Service — demonstrates declarative transaction patterns.
 *
 * <p>Transaction scenarios:
 *
 * <ol>
 *   <li><b>createOrder</b> — INSERT order + N items + deduct stock
 *       <br>Steps: validate → lock user → insert order → insert items → deduct stock per item
 *   <li><b>cancelOrder</b> — lock order + restore stock + update status
 *       <br>Steps: lock order FOR UPDATE → restore stock per item → update status
 * </ol>
 *
 * <p>Declarative transaction model: methods are annotated with {@link Transactional}.
 * The annotation is processed by {@link TransactionTemplate#wrap}; repository methods
 * auto-detect the active transaction via {@link com.example.db.TxContextHolder#current()}
 * and participate without explicit {@code TransactionContext} parameter passing.
 *
 * <p>Compare with the old manual pattern:
 * <pre>
 * // Old (manual):
 * DatabaseVerticle.withTransaction(vertx, tx ->
 *     orderRepo.findByIdForUpdate(tx, orderId)
 *         .compose(order -> orderRepo.updateStatusInTx(tx, orderId, "cancelled"))
 * );
 *
 * // New (declarative — no tx parameter!):
 * {@literal @}Transactional(timeoutMs = 30_000)
 * public Future&lt;Void&gt; cancelOrder(Long orderId) {
 *     return orderRepo.findByIdForUpdate(orderId)         // auto-detects tx
 *         .compose(order -> orderRepo.updateStatus(orderId, "cancelled")); // auto-detects tx
 * }
 * </pre>
 */
public class OrderService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderService.class);

    private final TransactionTemplate tx;
    private final OrderRepository orderRepo;
    private final ProductRepository productRepo;
    private final InventoryTransactionRepository invTxRepo;
    private final UserRepository userRepo;
    private final Vertx vertx;
    private final boolean dbAvailable;

    public OrderService(Vertx vertx) {
        this.vertx = vertx;
        this.tx = new TransactionTemplate(vertx);
        this.orderRepo = new OrderRepository(vertx);
        this.productRepo = new ProductRepository(vertx);
        this.invTxRepo = new InventoryTransactionRepository(vertx);
        this.userRepo = new UserRepository(vertx);
        this.dbAvailable = DatabaseVerticle.getPool(vertx) != null;
    }

    // ================================================================
    // Declarative Transaction: Create Order (3 tables: orders + order_items + products)
    // ================================================================

    /**
     * Create an order with multiple items — ALL OR NOTHING via {@link Transactional}.
     *
     * <p>Steps within ONE transaction:
     *   1. Lock user row (FOR UPDATE) — prevent concurrent order creation abuse
     *   2. INSERT orders row
     *   3. INSERT N order_items rows (sequential)
     *   4. Deduct stock for each product + record inventory ledger (sequential)
     *
     * <p>Any failure at any step → full rollback (no partial orders).
     *
     * <p>Note: {@code @Transactional} replaces the old
     * {@code DatabaseVerticle.withTransaction(vertx, tx -> ...)} wrapper.
     * Repository methods use auto-route via {@link com.example.db.TxContextHolder}.
     */
    @Transactional(timeoutMs = 60_000)
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
            return Future.failedFuture(
                BusinessException.badRequest("At least one item is required"));
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

        // No withTransaction wrapper — @Transactional handles it!
        // Repository calls auto-detect TxContextHolder.current() inside the annotated method.
        return userRepo.findByIdForUpdate(userId)  // no tx param
            // Step 2: Insert order
            .compose(user -> orderRepo.insertOrder(userId, orderTotal, remark))  // no tx param
            // Step 3: Insert items (sequential)
            .compose(orderId -> insertItemsSequence(orderId, items, 0)
                .map(orderId))
            // Step 4: Deduct stock + record ledger per item
            .compose(orderId -> deductStockSequence(orderId, items, 0)
                .map(orderId))
            // Step 5: Build result
            .map(orderId -> new JsonObject()
                .put("id", orderId)
                .put("userId", userId)
                .put("total", orderTotal)
                .put("remark", remark)
                .put("status", "pending")
                .put("itemCount", itemCount))
            .onSuccess(result -> LOG.info("[ORDER] Created id={}, userId={}, total={}, items={}",
                result.getLong("id"), userId, orderTotal, itemCount))
            .onFailure(err -> LOG.error("[ORDER] Create failed: {}", err.getMessage()));
    }

    // ================================================================
    // Declarative Transaction: Cancel Order (3 tables: orders + products + inventory_transactions)
    // ================================================================

    /**
     * Cancel an order — restore stock + update status in ONE transaction.
     *
     * <p>Both the status check and the update happen inside the same transaction
     * with FOR UPDATE lock. Concurrent cancel requests are serialised by the lock.
     *
     * <p>Steps within ONE transaction:
     *   1. Lock order row FOR UPDATE → concurrent cancels are blocked here
     *   2. Validate status (pending/paid only — completed/cancelled already handled)
     *   3. Restore stock for each item + record inventory ledger
     *   4. Update order status to cancelled
     */
    @Transactional(timeoutMs = 30_000)
    public Future<JsonObject> cancelOrder(Long orderId) {
        if (!dbAvailable) {
            return Future.failedFuture(BusinessException.serverError("Database not available"));
        }

        // Lock order row (prevents TOCTOU race) — no tx param needed!
        return orderRepo.findByIdForUpdate(orderId)  // auto-detects tx via TxContextHolder
            .compose(order -> {
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
            // Restore stock + record ledger — no tx param!
            .compose(order -> orderRepo.findItemsByOrderIdForTx(orderId)
                .compose(items -> restoreStockSequence(orderId, items, 0)
                    .map(order)))
            // Update status — no tx param!
            .compose(order -> orderRepo.updateStatus(orderId, "cancelled")
                .map(order))
            .onSuccess(result -> LOG.info("[ORDER] Cancelled id={}", orderId))
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

    /**
     * Insert order items one by one — stops on first failure.
     * Note: item methods auto-detect TxContextHolder.current() internally.
     */
    private Future<Void> insertItemsSequence(Long orderId, JsonArray items, int index) {
        if (index >= items.size()) return Future.succeededFuture();
        JsonObject item = items.getJsonObject(index);
        return orderRepo.insertItem(orderId, item.getLong("productId"),
                item.getInteger("quantity", 1),
                BigDecimal.valueOf(item.getDouble("price", 0.0)))
            .compose(v -> insertItemsSequence(orderId, items, index + 1));
    }

    /**
     * Deduct stock per item: lock product → deduct → record ledger.
     * Sequential to maintain audit order and stop at first stock failure.
     */
    private Future<Void> deductStockSequence(Long orderId, JsonArray items, int index) {
        if (index >= items.size()) return Future.succeededFuture();
        JsonObject item = items.getJsonObject(index);
        Long productId = item.getLong("productId");
        int quantity = item.getInteger("quantity", 1);

        // All three calls auto-detect TxContextHolder.current() — no explicit tx needed!
        return productRepo.findByIdForUpdate(productId)
            .compose(product -> {
                int before = product.getInteger("stock", 0);
                return productRepo.deductStock(productId, quantity, orderId)
                    .compose(after -> invTxRepo.recordDeduction(productId, orderId,
                        -quantity, before, after,
                        "Order " + orderId + ": deduct " + quantity + " unit(s)"))
                    .mapEmpty();
            })
            .compose(v -> deductStockSequence(orderId, items, index + 1));
    }

    /**
     * Restore stock per item: lock product → restore → record ledger.
     */
    private Future<Void> restoreStockSequence(Long orderId, JsonArray items, int index) {
        if (index >= items.size()) return Future.succeededFuture();
        JsonObject item = items.getJsonObject(index);
        Long productId = item.getLong("productId");
        int quantity = item.getInteger("quantity", 1);

        return productRepo.findByIdForUpdate(productId)
            .compose(product -> {
                int before = product.getInteger("stock", 0);
                return productRepo.restoreStock(productId, quantity, orderId)
                    .compose(after -> invTxRepo.recordRestoration(productId, orderId,
                        quantity, before, after,
                        "Order " + orderId + " cancelled: restore " + quantity + " unit(s)"))
                    .mapEmpty();
            })
            .compose(v -> restoreStockSequence(orderId, items, index + 1));
    }

    /**
     * Lock user row inside the current transaction.
     * Used by createOrder before inserting the order.
     * Note: findByIdForUpdate(userId) auto-detects TxContextHolder.
     */
    private Future<JsonObject> findUserForUpdate(Long userId) {
        return userRepo.findByIdForUpdate(userId)  // auto-route via TxContextHolder
            .map(user -> {
                if (user == null) throw new RuntimeException("User not found: " + userId);
                return user;
            });
    }
}
