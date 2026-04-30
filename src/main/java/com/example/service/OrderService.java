package com.example.service;

import com.example.core.BusinessException;
import com.example.core.PageResult;
import com.example.db.DatabaseVerticle;
import com.example.repository.OrderRepository;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

/**
 * Order Service — demonstrates multi-table transaction patterns.
 *
 * Key transaction scenarios:
 *   1. createOrder  — INSERT order + N items + deduct stock (3 tables, 1 transaction)
 *   2. cancelOrder  — UPDATE order status + restore stock (2 tables, 1 transaction)
 */
public class OrderService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepo;
    private final Vertx vertx;
    private final boolean dbAvailable;

    public OrderService(Vertx vertx) {
        this.vertx = vertx;
        this.orderRepo = new OrderRepository(vertx);
        this.dbAvailable = DatabaseVerticle.getPool(vertx) != null;
    }

    // ================================================================
    // Transactional: Create Order
    // ================================================================

    /**
     * Create an order with multiple items — all-or-nothing transaction.
     *
     * <p>Steps within one transaction:
     *   1. Validate & calculate total
     *   2. INSERT order row
     *   3. INSERT each order_item row
     *   4. Deduct stock for each product
     *
     * <p>If any step fails → entire transaction rolls back (no partial order).
     *
     * @param order JSON with: userId, remark, items[{productId, quantity}]
     * @return the created order with its ID
     */
    public Future<JsonObject> createOrder(JsonObject order) {
        if (!dbAvailable) {
            return Future.failedFuture(BusinessException.serverError("Database not available"));
        }

        Long userId = order.getLong("userId");
        JsonArray items = order.getJsonArray("items");
        String remark = order.getString("remark", "");

        // --- Validation ---
        if (userId == null) {
            return Future.failedFuture(BusinessException.badRequest("userId is required"));
        }
        if (items == null || items.isEmpty()) {
            return Future.failedFuture(BusinessException.badRequest("At least one item is required"));
        }

        // Pre-calculate total from the request
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

        // --- Execute in transaction ---
        return DatabaseVerticle.withTransaction(vertx, conn -> {
            // Step 1: Insert order
            return orderRepo.insertOrder(conn, userId, orderTotal, remark)
                .compose(orderId -> {
                    // Step 2: Insert items sequentially
                    return insertItemsSequence(conn, orderId, items, 0)
                        // Step 3: Deduct stock sequentially
                        .compose(v -> deductStockSequence(conn, items, 0))
                        // Step 4: Return result
                        .map(v -> new JsonObject()
                            .put("id", orderId)
                            .put("userId", userId)
                            .put("total", orderTotal)
                            .put("remark", remark)
                            .put("status", "pending")
                            .put("itemCount", itemCount));
                });
        }, 60_000)  // 60s timeout for order creation (may have many items)
        .onSuccess(result -> LOG.info("[ORDER] Created order id={}, userId={}, total={}, items={}",
            result.getLong("id"), userId, orderTotal, itemCount))
        .onFailure(err -> LOG.error("[ORDER] Create failed: {}", err.getMessage()));
    }

    // ================================================================
    // Transactional: Cancel Order
    // ================================================================

    /**
     * Cancel an order — restore stock & update status in one transaction.
     */
    public Future<JsonObject> cancelOrder(Long orderId) {
        if (!dbAvailable) {
            return Future.failedFuture(BusinessException.serverError("Database not available"));
        }

        // First, fetch the order + items (outside transaction for read)
        return orderRepo.findById(orderId)
            .compose(order -> {
                if (order == null) {
                    return Future.failedFuture(BusinessException.notFound("Order"));
                }
                String status = order.getString("status");
                if ("cancelled".equals(status)) {
                    return Future.failedFuture(
                        BusinessException.badRequest("Order already cancelled"));
                }
                if ("completed".equals(status)) {
                    return Future.failedFuture(
                        BusinessException.badRequest("Cannot cancel completed order"));
                }

                JsonArray items = order.getJsonArray("items", new JsonArray());

                // Transaction: restore stock + update status
                return DatabaseVerticle.withTransaction(vertx, conn -> {
                    return restoreStockSequence(conn, items, 0)
                        .compose(v -> orderRepo.updateStatus(conn, orderId, "cancelled"))
                        .map(v -> order.copy().put("status", "cancelled"));
                });
            });
    }

    // ================================================================
    // Read-only queries (no transaction needed)
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
    // Private helpers — sequential async loops inside transaction
    // ================================================================

    /**
     * Insert order items one by one (sequential to maintain order).
     * Using recursive Future chain instead of ParallelCompositeFuture
     * to guarantee item insertion order.
     */
    private Future<Void> insertItemsSequence(io.vertx.sqlclient.SqlConnection conn,
                                              Long orderId, JsonArray items, int index) {
        if (index >= items.size()) return Future.succeededFuture();

        JsonObject item = items.getJsonObject(index);
        Long productId = item.getLong("productId");
        int quantity = item.getInteger("quantity", 1);
        BigDecimal price = BigDecimal.valueOf(item.getDouble("price", 0.0));

        return orderRepo.insertItem(conn, orderId, productId, quantity, price)
            .compose(v -> insertItemsSequence(conn, orderId, items, index + 1));
    }

    /**
     * Deduct stock for each item sequentially.
     * If any product has insufficient stock → entire transaction rolls back.
     */
    private Future<Void> deductStockSequence(io.vertx.sqlclient.SqlConnection conn,
                                              JsonArray items, int index) {
        if (index >= items.size()) return Future.succeededFuture();

        JsonObject item = items.getJsonObject(index);
        Long productId = item.getLong("productId");
        int quantity = item.getInteger("quantity", 1);

        return orderRepo.deductStock(conn, productId, quantity)
            .compose(v -> deductStockSequence(conn, items, index + 1));
    }

    /**
     * Restore stock for each item (on order cancellation).
     */
    private Future<Void> restoreStockSequence(io.vertx.sqlclient.SqlConnection conn,
                                               JsonArray items, int index) {
        if (index >= items.size()) return Future.succeededFuture();

        JsonObject item = items.getJsonObject(index);
        Long productId = item.getLong("productId");
        int quantity = item.getInteger("quantity", 1);

        // Restore = negative deduction
        String sql = "UPDATE products SET stock = stock + $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2";
        io.vertx.sqlclient.Tuple params = io.vertx.sqlclient.Tuple.tuple()
            .addInteger(quantity).addLong(productId);

        return DatabaseVerticle.updateInTx(conn, sql, params)
            .compose(v -> restoreStockSequence(conn, items, index + 1));
    }
}
