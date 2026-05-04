package com.example.service.impl;

import com.example.core.BusinessException;
import com.example.db.*;
import com.example.repository.InventoryTransactionRepository;
import com.example.repository.OrderRepository;
import com.example.repository.PaymentRepository;
import com.example.repository.ProductRepository;
import com.example.repository.UserRepository;
import com.example.service.PaymentService;
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
 * Payment Service Implementation — 5-table / 4-table multi-Repository declarative transactions.
 */
public class PaymentServiceImpl implements PaymentService {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentRepository paymentRepo;
    private final OrderRepository orderRepo;
    private final UserRepository userRepo;
    private final ProductRepository productRepo;
    private final InventoryTransactionRepository invTxRepo;
    private final AuditLogger audit;
    private final Vertx vertx;
    private final boolean dbAvailable;

    public PaymentServiceImpl(Vertx vertx) {
        this.vertx = vertx;
        this.paymentRepo = new PaymentRepository(vertx);
        this.orderRepo = new OrderRepository(vertx);
        this.userRepo = new UserRepository(vertx);
        this.productRepo = new ProductRepository(vertx);
        this.invTxRepo = new InventoryTransactionRepository(vertx);
        this.audit = new AuditLogger(vertx);
        this.dbAvailable = DatabaseVerticle.getPool(vertx) != null;
    }

    @Override
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
            .compose(order -> {
                Long userId = order.getLong("user_id");
                BigDecimal total = new BigDecimal(order.getString("total", "0"));
                if ("balance".equals(method)) {
                    return userRepo.deductBalance(userId, total).map(order);
                }
                return Future.succeededFuture(order);
            })
            .compose(order -> {
                Long userId = order.getLong("user_id");
                BigDecimal amount = new BigDecimal(order.getString("total", "0"));
                return paymentRepo.insertPayment(orderId, userId, amount, method, "pending").map(order);
            })
            .compose(order -> paymentRepo.findByOrderIdForTx(orderId)
                .compose(payment -> paymentRepo.updatePaymentStatus(payment.getLong("id"), "completed").map(payment))
                .map(order))
            .compose(order -> orderRepo.updateStatus(orderId, "completed").map(order))
            .compose(order -> audit.log(AuditAction.AUDIT_CREATE, "payments",
                    String.valueOf(orderId), null, null).map(order))
            .compose(order -> confirmStockAndLedger(orderId).map(order))
            .compose(order -> {
                Long userId = order.getLong("user_id");
                return userRepo.updateUserOrderCount(userId, 1).map(order);
            })
            .compose(order -> paymentRepo.findByOrderIdForTx(orderId)
                .map(payment -> payment.copy()
                    .put("orderTotal", order.getString("total"))
                    .put("orderStatus", "completed")
                    .put("userName", order.getString("user_name"))));
    }

    @Override
    @Transactional(timeoutMs = 20_000)
    public Future<JsonObject> refundPayment(Long paymentId) {
        if (!dbAvailable) {
            return Future.failedFuture(BusinessException.serverError("Database not available"));
        }

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
            .compose(payment -> {
                Long userId = payment.getLong("user_id");
                BigDecimal amount = new BigDecimal(payment.getString("amount", "0"));
                String method = payment.getString("method", "balance");
                if ("balance".equals(method)) {
                    return userRepo.addBalance(userId, amount).map(payment);
                }
                return Future.succeededFuture(payment);
            })
            .compose(payment -> {
                JsonObject oldVal = payment.copy();
                return paymentRepo.updatePaymentStatus(paymentId, "refunded").map(oldVal);
            })
            .compose(oldVal -> {
                Long orderId = oldVal.getLong("order_id");
                return orderRepo.updateStatus(orderId, "refunded").map(oldVal);
            })
            .compose(oldVal -> {
                JsonObject newVal = oldVal.copy().put("status", "refunded");
                return audit.log(AuditAction.AUDIT_UPDATE, "payments",
                    String.valueOf(paymentId), oldVal, newVal).map(oldVal);
            })
            .compose(oldVal -> findPaymentEnriched(paymentId).map(p -> p != null ? p : oldVal))
            .onSuccess(result -> LOG.info("[PAY] Refunded: paymentId={}", paymentId))
            .onFailure(err -> LOG.error("[PAY] Refund failed: paymentId={}, err={}", paymentId, err.getMessage()));
    }

    @Override
    public Future<JsonObject> findById(Long id) {
        if (!dbAvailable) return Future.succeededFuture(null);
        return paymentRepo.findById(id)
            .map(p -> {
                if (p == null) throw BusinessException.notFound("Payment");
                return p;
            });
    }

    @Override
    public Future<JsonObject> findByOrderId(Long orderId) {
        if (!dbAvailable) return Future.succeededFuture(null);
        return paymentRepo.findByOrderId(orderId);
    }

    @Override
    public Future<List<JsonObject>> findByUserId(Long userId) {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return paymentRepo.findByUserId(userId);
    }

    @Override
    public Future<List<JsonObject>> findByStatus(String status) {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return paymentRepo.findByStatus(status);
    }

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
