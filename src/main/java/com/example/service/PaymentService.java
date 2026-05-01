package com.example.service;

import com.example.core.BusinessException;
import com.example.db.AuditAction;
import com.example.db.AuditLogger;
import com.example.db.DatabaseVerticle;
import com.example.db.Transactional;
import com.example.db.TransactionContext;
import com.example.db.TransactionTemplate;
import com.example.db.TxContextHolder;
import com.example.repository.InventoryTransactionRepository;
import com.example.repository.OrderRepository;
import com.example.repository.PaymentRepository;
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
 * Payment Service — 5-table / 4-table multi-Repository declarative transactions.
 *
 * <p>Two transactional scenarios:
 * <ol>
 *   <li><b>processPayment</b> — payments + orders + users + products + inventory_transactions
 *   <li><b>refundPayment</b>  — payments + orders + users + inventory_transactions
 * </ol>
 *
 * <p>All repository calls auto-detect {@link TxContextHolder#current()}.
 * {@link Transactional} annotation on public methods replaces
 * {@code DatabaseVerticle.withTransaction(vertx, tx -> ...)} wrapper.
 */
public class PaymentService {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentService.class);

    private final TransactionTemplate tx;
    private final PaymentRepository paymentRepo;
    private final OrderRepository orderRepo;
    private final UserRepository userRepo;
    private final ProductRepository productRepo;
    private final InventoryTransactionRepository invTxRepo;
    private final AuditLogger audit;
    private final Vertx vertx;
    private final boolean dbAvailable;

    public PaymentService(Vertx vertx) {
        this.vertx = vertx;
        this.tx = new TransactionTemplate(vertx);
        this.paymentRepo = new PaymentRepository(vertx);
        this.orderRepo = new OrderRepository(vertx);
        this.userRepo = new UserRepository(vertx);
        this.productRepo = new ProductRepository(vertx);
        this.invTxRepo = new InventoryTransactionRepository(vertx);
        this.audit = new AuditLogger(vertx);
        this.dbAvailable = DatabaseVerticle.getPool(vertx) != null;
    }

    // ================================================================
    // Declarative Transaction: Process Payment (5 tables)
    // ================================================================

    /**
     * Process a payment for an order — ALL 5 TABLES in one transaction.
     *
     * <p>Idempotent: if a completed payment already exists, returns it without re-processing.
     * Repository calls auto-detect {@link TxContextHolder#current()}.
     */
    @Transactional(timeoutMs = 30_000)
    public Future<JsonObject> processPayment(JsonObject request) {
        if (!dbAvailable) {
            return Future.failedFuture(BusinessException.serverError("Database not available"));
        }

        Long orderId = request.getLong("orderId");
        String method = request.getString("method", "balance");

        if (orderId == null) {
            return Future.failedFuture(BusinessException.badRequest("orderId is required"));
        }

        // Idempotency check
        return paymentRepo.findByOrderIdForTx(orderId)
            .compose(existing -> {
                if (existing != null && "completed".equals(existing.getString("status"))) {
                    LOG.info("[PAY] Already processed: orderId={}", orderId);
                    return Future.succeededFuture(existing.copy().put("_idempotent", true));
                }
                return doProcessPayment(orderId, method);
            })
            .onSuccess(result -> LOG.info("[PAY] Processed: orderId={}, paymentId={}, method={}",
                orderId, result.getLong("id"), method))
            .onFailure(err -> LOG.error("[PAY] Failed: orderId={}, err={}", orderId, err.getMessage()));
    }

    private Future<JsonObject> doProcessPayment(Long orderId, String method) {
        // Step 1: Lock & validate order
        return orderRepo.findByIdForUpdate(orderId)
            .compose(order -> {
                String status = order.getString("status");
                if ("paid".equals(status) || "completed".equals(status)) {
                    return Future.<JsonObject>failedFuture(
                        BusinessException.conflict("Order already paid: " + orderId));
                }
                if ("cancelled".equals(status)) {
                    return Future.<JsonObject>failedFuture(
                        BusinessException.badRequest("Cannot pay cancelled order: " + orderId));
                }
                return Future.succeededFuture(order);
            })
            // Step 2: Lock user & deduct balance (for balance payments)
            .compose(order -> {
                Long userId = order.getLong("user_id");
                BigDecimal total = new BigDecimal(order.getString("total", "0"));
                if ("balance".equals(method)) {
                    return userRepo.deductBalance(userId, total).map(order);
                }
                return Future.succeededFuture(order);
            })
            // Step 3: Insert pending payment
            .compose(order -> {
                Long userId = order.getLong("user_id");
                BigDecimal amount = new BigDecimal(order.getString("total", "0"));
                return paymentRepo.insertPayment(orderId, userId, amount, method, "pending")
                    .map(order);
            })
            // Step 4: Update payment to completed
            .compose(order ->
                paymentRepo.findByOrderIdForTx(orderId)
                    .compose(payment -> paymentRepo.updatePaymentStatus(payment.getLong("id"), "completed"))
                    .map(order))
            // Step 5: Update order status
            .compose(order -> orderRepo.updateStatus(orderId, "completed").map(order))
            // Step 6: Audit log for payment creation (模式A: 事务内)
            .compose(order -> paymentRepo.findByOrderIdForTx(orderId)
                .compose((JsonObject payment) -> audit.logInTx(TxContextHolder.current(),
                        AuditAction.AUDIT_CREATE, "payments",
                        String.valueOf(payment.getLong("id")),
                        null, payment)
                    .map(payment)))
            // Step 7: Confirm stock + record inventory ledger
            .compose((JsonObject order) -> confirmStockAndLedger(orderId).map(order))
            // Step 8: Increment user order_count
            .compose((JsonObject order) -> {
                Long userId = order.getLong("user_id");
                return userRepo.updateUserOrderCount(userId, 1).map(order);
            })
            // Step 9: Return enriched payment record
            .compose((JsonObject order) ->
                paymentRepo.findByOrderIdForTx(orderId)
                    .map(payment -> payment.copy()
                        .put("orderTotal", order.getString("total"))
                        .put("orderStatus", "completed")
                        .put("userName", order.getString("user_name"))));
    }

    // ================================================================
    // Declarative Transaction: Refund Payment (4 tables)
    // ================================================================

    /**
     * Refund a completed payment — restore balance + update statuses in ONE transaction.
     *
     * <p>Idempotent: returns existing refund if already processed.
     *
     * <p>Steps within ONE transaction:
     *   1. Lock payment FOR UPDATE
     *   2. Restore balance (for balance payments)
     *   3. Update payment status → refunded
     *   4. Update order status → refunded
     */
    @Transactional(timeoutMs = 20_000)
    public Future<JsonObject> refundPayment(Long paymentId) {
        if (!dbAvailable) {
            return Future.failedFuture(BusinessException.serverError("Database not available"));
        }

        // Step 1: Lock payment FOR UPDATE
        return lockPaymentForUpdate(paymentId)
            .compose(payment -> {
                if (payment == null) {
                    return Future.<JsonObject>failedFuture(BusinessException.notFound("Payment"));
                }
                String status = payment.getString("status");
                if ("refunded".equals(status)) {
                    return Future.succeededFuture(payment.copy().put("_idempotent", true));
                }
                if (!"completed".equals(status)) {
                    return Future.<JsonObject>failedFuture(
                        BusinessException.badRequest(
                            "Can only refund completed payments, current status: " + status));
                }
                return Future.succeededFuture(payment);
            })
            // Step 2: Restore balance (for balance payments)
            .compose(payment -> {
                Long userId = payment.getLong("user_id");
                BigDecimal amount = new BigDecimal(payment.getString("amount", "0"));
                String method = payment.getString("method", "balance");
                if ("balance".equals(method)) {
                    return userRepo.addBalance(userId, amount).map(payment);
                }
                return Future.succeededFuture(payment);
            })
            // Step 3: Update payment status → refunded
            .compose((JsonObject payment) -> paymentRepo.updatePaymentStatus(paymentId, "refunded").map(payment))
            // Step 4: Update order status → refunded
            .compose((JsonObject payment) -> {
                Long orderId = payment.getLong("order_id");
                return orderRepo.updateStatus(orderId, "refunded").map(payment);
            })
            // Step 5: Audit log (模式A: 事务内)
            .compose((JsonObject payment) -> {
                JsonObject oldVal = payment.copy();
                JsonObject newVal = payment.copy().put("status", "refunded");
                return audit.logInTx(TxContextHolder.current(),
                        AuditAction.AUDIT_UPDATE, "payments",
                        String.valueOf(paymentId),
                        oldVal, newVal)
                    .map(payment);
            })
            // Step 6: Return enriched payment
            .compose((JsonObject payment) -> findPaymentEnriched(paymentId).map(p -> p != null ? p : payment))
            .onSuccess(result -> LOG.info("[PAY] Refunded: paymentId={}", paymentId))
            .onFailure(err -> LOG.error("[PAY] Refund failed: paymentId={}, err={}", paymentId, err.getMessage()));
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
     * Lock a payment row FOR UPDATE inside the current transaction.
     * Uses {@link TxContextHolder#current()} — only valid inside a {@link Transactional} method.
     */
    private Future<JsonObject> lockPaymentForUpdate(Long paymentId) {
        TransactionContext tx = TxContextHolder.current();
        if (tx == null) {
            return Future.failedFuture(new IllegalStateException(
                "lockPaymentForUpdate called outside a transaction"));
        }
        tx.tick();
        String sql = "SELECT * FROM payments WHERE id = $1 FOR UPDATE";
        return DatabaseVerticle.queryOneInTx(tx.conn(), sql, Tuple.tuple().addLong(paymentId));
    }

    /**
     * Return enriched payment with order + user details.
     * Uses {@link TxContextHolder#current()} — only valid inside a {@link Transactional} method.
     */
    private Future<JsonObject> findPaymentEnriched(Long paymentId) {
        TransactionContext tx = TxContextHolder.current();
        if (tx == null) {
            return Future.failedFuture(new IllegalStateException(
                "findPaymentEnriched called outside a transaction"));
        }
        tx.tick();
        String sql = "SELECT p.*, o.total as order_total, o.status as order_status, " +
            "u.name as user_name FROM payments p " +
            "LEFT JOIN orders o ON p.order_id = o.id " +
            "LEFT JOIN users u ON p.user_id = u.id WHERE p.id = $1";
        return DatabaseVerticle.queryOneInTx(tx.conn(), sql, Tuple.tuple().addLong(paymentId));
    }

    /**
     * Confirm stock deduction and record inventory ledger.
     * In "pre-deduct on create" model: stock was already reduced at order creation.
     * On payment confirmation, we write a zero-delta ledger entry.
     */
    private Future<Void> confirmStockAndLedger(Long orderId) {
        return orderRepo.findItemsByOrderIdForTx(orderId)
            .compose(this::confirmStockLoop);
    }

    private Future<Void> confirmStockLoop(JsonArray items) {
        return confirmStockLoopImpl(items, 0);
    }

    private Future<Void> confirmStockLoopImpl(JsonArray items, int index) {
        if (index >= items.size()) return Future.succeededFuture();
        JsonObject item = items.getJsonObject(index);
        Long productId = item.getLong("product_id");
        Long orderId = item.getLong("order_id");

        // All calls auto-detect TxContextHolder — no explicit tx parameter needed
        return productRepo.findByIdForUpdate(productId)
            .compose(product -> {
                int stockAfter = product.getInteger("stock", 0);
                return invTxRepo.recordRestoration(productId, orderId,
                    0, stockAfter, stockAfter,
                    "Payment confirmed for order " + orderId +
                        ": stock already deducted at creation");
            })
            .compose(v -> confirmStockLoopImpl(items, index + 1));
    }
}
