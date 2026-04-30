package com.example.service;

import com.example.core.BusinessException;
import com.example.core.PageResult;
import com.example.db.DatabaseVerticle;
import com.example.db.TransactionContext;
import com.example.repository.InventoryTransactionRepository;
import com.example.repository.OrderRepository;
import com.example.repository.PaymentRepository;
import com.example.repository.ProductRepository;
import com.example.repository.UserRepository;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payment Service — demonstrates a complex multi-table transaction scenario.
 *
 * <p>This service handles the atomic payment flow: when a user pays for an order,
 * 5 database tables are modified within a single ACID transaction:
 *
 * <ol>
 *   <li><b>payments</b>         — record the payment transaction
 *   <li><b>orders</b>            — update order status: pending → paid/completed
 *   <li><b>products</b>         — deduct stock (stock already deducted at order creation,
 *                                  but confirmed on payment in this model)
 *   <li><b>inventory_transactions</b> — ledger entry for stock confirmation
 *   <li><b>users</b>             — deduct user balance (for balance payments)
 * </ol>
 *
 * <p><b>Transaction: processPayment (5 tables, 4 Repositories)</b>
 * <pre>
 * BEGIN
 *   1. Lock order FOR UPDATE          → OrderRepository.findByIdForUpdate
 *   2. Lock user FOR UPDATE           → UserRepository.findByIdForUpdate
 *   3. Deduct user balance            → UserRepository.deductBalance
 *   4. Insert payment record          → PaymentRepository.insertPayment
 *   5. Update payment to completed    → PaymentRepository.updateStatus
 *   6. Update order to completed     → OrderRepository.updateStatusInTx
 *   7. Confirm stock deduction        → ProductRepository + InventoryTransactionRepository
 *   8. Increment user order_count     → UserRepository.updateUserOrderCount
 * COMMIT / ROLLBACK
 * </pre>
 *
 * <p><b>Transaction: refundPayment (4 tables, 3 Repositories)</b>
 * Similar structure: lock payment → lock order → restore balance → record refund → update statuses.
 *
 * <p>Idempotency: both processPayment and refundPayment check for existing
 * payment records before proceeding, preventing duplicate payment processing.
 */
public class PaymentService {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepo;
    private final OrderRepository orderRepo;
    private final UserRepository userRepo;
    private final ProductRepository productRepo;
    private final InventoryTransactionRepository invTxRepo;
    private final Vertx vertx;
    private final boolean dbAvailable;

    public PaymentService(Vertx vertx) {
        this.vertx = vertx;
        this.paymentRepo = new PaymentRepository(vertx);
        this.orderRepo = new OrderRepository(vertx);
        this.userRepo = new UserRepository(vertx);
        this.productRepo = new ProductRepository(vertx);
        this.invTxRepo = new InventoryTransactionRepository(vertx);
        this.dbAvailable = DatabaseVerticle.getPool(vertx) != null;
    }

    // ================================================================
    // Transaction: Process Payment
    // ================================================================

    /**
     * Process a payment for an order — ALL 5 TABLES in one transaction.
     *
     * <p>Idempotent: if a completed payment already exists for this order,
     * returns it without re-processing.
     *
     * @param request JSON with: orderId, method (balance|card|alipay|wechat)
     * @return payment record with order and user details
     */
    public Future<JsonObject> processPayment(JsonObject request) {
        if (!dbAvailable) {
            return Future.failedFuture(BusinessException.serverError("Database not available"));
        }

        Long orderId = request.getLong("orderId");
        String method = request.getString("method", "balance");

        if (orderId == null) {
            return Future.failedFuture(BusinessException.badRequest("orderId is required"));
        }

        return DatabaseVerticle.withTransaction(vertx, tx ->
            // Step 1: Idempotency — check if already paid
            paymentRepo.findByOrderIdInTx(tx, orderId)
                .compose(existing -> {
                    if (existing != null && "completed".equals(existing.getString("status"))) {
                        LOG.info("[PAY] Already processed: orderId={}", orderId);
                        return Future.succeededFuture(existing.copy()
                            .put("_idempotent", true));
                    }
                    return doProcessPayment(tx, orderId, method);
                }),
            30_000  // 30s timeout
        ).onSuccess(result -> LOG.info("[PAY] Processed: orderId={}, paymentId={}, method={}",
            orderId, result.getLong("id"), method))
        .onFailure(err -> LOG.error("[PAY] Failed: orderId={}, err={}", orderId, err.getMessage()));
    }

    private Future<JsonObject> doProcessPayment(TransactionContext tx, Long orderId, String method) {
        // Step 2: Lock & validate order
        return orderRepo.findByIdForUpdate(tx, orderId)
            .compose(order -> {
                String status = order.getString("status");
                if ("paid".equals(status) || "completed".equals(status)) {
                    return Future.failedFuture(
                        BusinessException.conflict("Order already paid: " + orderId));
                }
                if ("cancelled".equals(status)) {
                    return Future.failedFuture(
                        BusinessException.badRequest("Cannot pay cancelled order: " + orderId));
                }
                return Future.succeededFuture(order);
            })
            // Step 3: Lock & validate user
            .compose(order -> {
                Long userId = order.getLong("user_id");
                return userRepo.findByIdForUpdate(tx, userId)
                    .compose(user -> {
                        BigDecimal total = new BigDecimal(order.getString("total", "0"));
                        // Step 4: Deduct balance (for balance payments)
                        if ("balance".equals(method)) {
                            return userRepo.deductBalance(tx, userId, total)
                                .map(order);
                        }
                        return Future.succeededFuture(order);
                    });
            })
            // Step 5: Insert pending payment
            .compose(order -> {
                Long userId = order.getLong("user_id");
                BigDecimal amount = new BigDecimal(order.getString("total", "0"));
                return paymentRepo.insertPayment(tx, orderId, userId, amount, method, "pending")
                    .map(order);
            })
            // Step 6: Update payment to completed
            .compose(order -> {
                // Find the payment we just inserted (no paymentId variable scope issue)
                return paymentRepo.findByOrderIdInTx(tx, orderId)
                    .compose(payment -> {
                        Long paymentId = payment.getLong("id");
                        return paymentRepo.updateStatus(tx, paymentId, "completed")
                            .map(order);
                    });
            })
            // Step 7: Update order status
            .compose(order -> orderRepo.updateStatusInTx(tx, orderId, "completed").map(order))
            // Step 8: Confirm stock + record inventory ledger
            .compose(order ->
                confirmStockAndLedger(tx, orderId).map(order))
            // Step 9: Increment user order_count
            .compose(order -> {
                Long userId = order.getLong("user_id");
                return userRepo.updateUserOrderCount(tx, userId, 1).map(order);
            })
            // Step 10: Return enriched payment record
            .compose(order -> {
                Long userId = order.getLong("user_id");
                return paymentRepo.findByOrderIdInTx(tx, orderId)
                    .map(payment -> payment.copy()
                        .put("orderTotal", order.getString("total"))
                        .put("orderStatus", "completed")
                        .put("userName", order.getString("user_name")));
            });
    }

    // ================================================================
    // Transaction: Refund Payment
    // ================================================================

    /**
     * Refund a payment — restore balance, update payment + order status.
     *
     * <p>Steps within ONE transaction:
     *   1. Lock payment FOR UPDATE → validate not already refunded
     *   2. Lock user → restore balance
     *   3. Update payment status to refunded
     *   4. Update order status to refunded
     *
     * <p>Idempotent: returns existing refund if already processed.
     */
    public Future<JsonObject> refundPayment(Long paymentId) {
        if (!dbAvailable) {
            return Future.failedFuture(BusinessException.serverError("Database not available"));
        }

        return DatabaseVerticle.withTransaction(vertx, tx -> {
            // Step 1: Find and lock payment
            String sql = "SELECT * FROM payments WHERE id = $1 FOR UPDATE";
            return DatabaseVerticle.queryOneInTx(tx.conn(), sql,
                    io.vertx.sqlclient.Tuple.tuple().addLong(paymentId))
                .compose(payment -> {
                    if (payment == null) {
                        return Future.failedFuture(BusinessException.notFound("Payment"));
                    }
                    String status = payment.getString("status");
                    if ("refunded".equals(status)) {
                        return Future.succeededFuture(payment.copy().put("_idempotent", true));
                    }
                    if (!"completed".equals(status)) {
                        return Future.failedFuture(
                            BusinessException.badRequest(
                                "Can only refund completed payments, current status: " + status));
                    }
                    return Future.succeededFuture(payment);
                })
                // Step 2: Lock user → restore balance
                .compose(payment -> {
                    Long userId = payment.getLong("user_id");
                    BigDecimal amount = new BigDecimal(payment.getString("amount", "0"));
                    String method = payment.getString("method", "balance");
                    if ("balance".equals(method)) {
                        return userRepo.addBalance(tx, userId, amount)
                            .map(payment);
                    }
                    return Future.succeededFuture(payment);
                })
                // Step 3: Update payment status
                .compose(payment -> paymentRepo.updateStatus(tx, paymentId, "refunded").map(payment))
                // Step 4: Update order status
                .compose(payment -> {
                    Long orderId = payment.getLong("order_id");
                    return orderRepo.updateStatusInTx(tx, orderId, "refunded")
                        .map(payment);
                })
                // Step 5: Return updated payment
                .compose(payment -> {
                    String sql2 = "SELECT p.*, o.total as order_total, o.status as order_status, " +
                        "u.name as user_name FROM payments p " +
                        "LEFT JOIN orders o ON p.order_id = o.id " +
                        "LEFT JOIN users u ON p.user_id = u.id WHERE p.id = $1";
                    return DatabaseVerticle.queryOneInTx(tx.conn(), sql2,
                        io.vertx.sqlclient.Tuple.tuple().addLong(paymentId))
                        .map(p -> p != null ? p : payment);
                });
        }, 20_000);
    }

    // ================================================================
    // Read-only queries
    // ================================================================

    public Future<JsonObject> findById(Long id) {
        if (!dbAvailable) return Future.succeededFuture(null);
        return paymentRepo.findById(id)
            .map(p -> {
                if (p == null) throw BusinessException.notFound("Payment");
                return p;
            });
    }

    public Future<JsonObject> findByOrderId(Long orderId) {
        if (!dbAvailable) return Future.succeededFuture(null);
        return paymentRepo.findByOrderId(orderId);
    }

    public Future<List<JsonObject>> findByUserId(Long userId) {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return paymentRepo.findByUserId(userId);
    }

    public Future<List<JsonObject>> findByStatus(String status) {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return paymentRepo.findByStatus(status);
    }

    // ================================================================
    // Private helpers
    // ================================================================

    /**
     * Confirm stock deduction for all order items and record inventory ledger.
     * In this model, stock was pre-deducted on order creation.
     * On payment confirmation, we log the "confirmed" ledger entry.
     *
     * <p>If stock was NOT pre-deducted (separate model), use deductStockSequence here instead.
     */
    private Future<Void> confirmStockAndLedger(TransactionContext tx, Long orderId) {
        return orderRepo.findItemsByOrderIdInTx(tx, orderId)
            .compose(items -> confirmStockLoop(tx, orderId, items, 0));
    }

    private Future<Void> confirmStockLoop(TransactionContext tx, Long orderId,
                                           JsonArray items, int index) {
        if (index >= items.size()) return Future.succeededFuture();
        JsonObject item = items.getJsonObject(index);
        Long productId = item.getLong("product_id");
        int quantity = item.getInteger("quantity");

        return productRepo.findByIdForUpdate(tx, productId)
            .compose(product -> {
                // In "pre-deduct on create" model: stock is already reduced.
                // Ledger was written on order creation (order_create).
                // Here we write a confirmation entry (no delta change).
                int stockAfter = product.getInteger("stock", 0);
                return invTxRepo.recordRestoration(tx, productId, orderId,
                    0, stockAfter, stockAfter,
                    "Payment confirmed for order " + orderId + ": stock already deducted at creation")
                    .mapEmpty();
            })
            .compose(v -> confirmStockLoop(tx, orderId, items, index + 1));
    }
}
