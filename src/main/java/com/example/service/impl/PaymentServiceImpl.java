package com.example.service.impl;

import com.example.core.BusinessException;
import com.example.db.*;
import com.example.entity.Order;
import com.example.entity.OrderItem;
import com.example.entity.Payment;
import com.example.entity.Product;
import com.example.repository.InventoryTransactionRepository;
import com.example.repository.OrderRepository;
import com.example.repository.PaymentRepository;
import com.example.repository.ProductRepository;
import com.example.repository.UserRepository;
import com.example.service.PaymentService;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payment Service Implementation — 5-table / 4-table multi-Repository declarative transactions.
 */
public class PaymentServiceImpl implements PaymentService {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final Vertx vertx;
    private final PaymentRepository paymentRepo;
    private final OrderRepository orderRepo;
    private final UserRepository userRepo;
    private final ProductRepository productRepo;
    private final InventoryTransactionRepository invTxRepo;
    private final TransactionTemplate txTemplate;

    public PaymentServiceImpl(Vertx vertx) {
        this.vertx = vertx;
        this.paymentRepo = new PaymentRepository(vertx);
        this.orderRepo = new OrderRepository(vertx);
        this.userRepo = new UserRepository(vertx);
        this.productRepo = new ProductRepository(vertx);
        this.invTxRepo = new InventoryTransactionRepository(vertx);
        this.txTemplate = new TransactionTemplate(vertx);
    }
    @Override
    public Future<Payment> processPayment(JsonObject request) {
        Long orderId = request.getLong("orderId");
        String method = request.getString("method", "balance");

        if (orderId == null) {
            return Future.failedFuture(BusinessException.badRequest("orderId is required"));
        }

        return txTemplate.wrap(tx -> doProcessPayment(orderId, method), 30_000)
            .onSuccess(result -> LOG.info("[PAY] Processed: orderId={}, paymentId={}, method={}",
                orderId, result.getId(), method))
            .onFailure(err -> LOG.error("[PAY] Failed: orderId={}, err={}", orderId, err.getMessage()));
    }

    private Future<Payment> doProcessPayment(Long orderId, String method) {
        return paymentRepo.findByOrderIdForTx(orderId)
            .compose(existing -> {
                if (existing != null && "completed".equals(existing.getStatus())) {
                    LOG.info("[PAY] Already processed: orderId={}", orderId);
                    existing.setRemark("_idempotent");
                    return Future.succeededFuture(existing);
                }
                return doProcessPaymentImpl(orderId, method);
            });
    }

    private Future<Payment> doProcessPaymentImpl(Long orderId, String method) {
        return orderRepo.findByIdForUpdate(orderId)
            .compose(order -> {
                String status = order.getStatus();
                if ("paid".equals(status) || "completed".equals(status)) {
                    return Future.<Order>failedFuture(
                        BusinessException.conflict("Order already paid: " + orderId));
                }
                if ("cancelled".equals(status)) {
                    return Future.<Order>failedFuture(
                        BusinessException.badRequest("Cannot pay cancelled order: " + orderId));
                }
                return Future.succeededFuture(order);
            })
            .compose(order -> {
                Long userId = order.getUserId();
                BigDecimal total = order.getTotal();
                if ("balance".equals(method)) {
                    return userRepo.deductBalance(userId, total).map(order);
                }
                return Future.succeededFuture(order);
            })
            .compose(order -> {
                Long userId = order.getUserId();
                BigDecimal amount = order.getTotal();
                return paymentRepo.insertPayment(orderId, userId, amount, method, "pending").map(order);
            })
            .compose(order -> paymentRepo.findByOrderIdForTx(orderId)
                .compose(payment -> paymentRepo.updatePaymentStatus(payment.getId(), "completed").map(payment))
                .map(order))
            .compose(order -> orderRepo.updateStatus(orderId, "completed").map(order))
            .compose(order -> confirmStockAndLedger(orderId).map(order))
            .compose(order -> {
                Long userId = order.getUserId();
                return userRepo.updateUserOrderCount(userId, 1).map(order);
            })
            .compose(order -> paymentRepo.findByOrderIdForTx(orderId)
                .map(payment -> {
                    payment.setOrderTotal(order.getTotal());
                    payment.setOrderStatus("completed");
                    payment.setUserName(order.getUserName());
                    return payment;
                }));
    }

    @Override
    public Future<Payment> refundPayment(Long paymentId) {
        return txTemplate.wrap(tx -> doRefundPayment(paymentId), 20_000)
            .onSuccess(result -> LOG.info("[PAY] Refunded: paymentId={}", paymentId))
            .onFailure(err -> LOG.error("[PAY] Refund failed: paymentId={}, err={}", paymentId, err.getMessage()));
    }

    private Future<Payment> doRefundPayment(Long paymentId) {
        return lockPaymentForUpdate(paymentId)
            .compose(payment -> {
                if (payment == null) {
                    return Future.<Payment>failedFuture(BusinessException.notFound("Payment"));
                }
                String status = payment.getStatus();
                if ("refunded".equals(status)) {
                    payment.setRemark("_idempotent");
                    return Future.succeededFuture(payment);
                }
                if (!"completed".equals(status)) {
                    return Future.<Payment>failedFuture(
                        BusinessException.badRequest(
                            "Can only refund completed payments, current status: " + status));
                }
                return Future.succeededFuture(payment);
            })
            .compose(payment -> {
                Long userId = payment.getUserId();
                BigDecimal amount = payment.getAmount();
                String method = payment.getMethod();
                if ("balance".equals(method)) {
                    return userRepo.addBalance(userId, amount).map(payment);
                }
                return Future.succeededFuture(payment);
            })
            .compose(payment -> paymentRepo.updatePaymentStatus(paymentId, "refunded").map(payment))
            .compose(payment -> {
                Long orderId = payment.getOrderId();
                return orderRepo.updateStatus(orderId, "refunded").map(payment);
            })
            .compose(payment -> findPaymentEnriched(paymentId)
                .recover(err -> Future.succeededFuture(payment)));
    }

    @Override
    public Future<Payment> findById(Long id) {
        return paymentRepo.findById(id)
            .map(p -> {
                if (p == null) throw BusinessException.notFound("Payment");
                return p;
            });
    }

    @Override
    public Future<Payment> findByOrderId(Long orderId) {
        return paymentRepo.findByOrderId(orderId);
    }

    @Override
    public Future<List<Payment>> findByUserId(Long userId) {
        return paymentRepo.findByUserId(userId);
    }

    @Override
    public Future<List<Payment>> findByStatus(String status) {
        return paymentRepo.findByStatus(status);
    }

    private Future<Payment> lockPaymentForUpdate(Long paymentId) {
        TransactionContext tx = TxContextHolder.current();
        if (tx == null) {
            return Future.failedFuture(BusinessException.badRequest(
                "Payment operation requires active transaction"));
        }
        tx.tick();
        String sql = "SELECT * FROM payments WHERE id = $1 FOR UPDATE";
        return DatabaseVerticle.queryOneInTx(tx.conn(), sql, io.vertx.sqlclient.Tuple.tuple().addLong(paymentId))
            .map(json -> json != null ? toPayment(json) : null);
    }

    private Future<Payment> findPaymentEnriched(Long paymentId) {
        TransactionContext tx = TxContextHolder.current();
        if (tx == null) {
            return Future.failedFuture(BusinessException.badRequest(
                "Payment query requires active transaction"));
        }
        tx.tick();
        String sql = "SELECT p.*, o.total as order_total, o.status as order_status, " +
            "u.name as user_name FROM payments p " +
            "LEFT JOIN orders o ON p.order_id = o.id " +
            "LEFT JOIN users u ON p.user_id = u.id WHERE p.id = $1";
        return DatabaseVerticle.queryOneInTx(tx.conn(), sql, io.vertx.sqlclient.Tuple.tuple().addLong(paymentId))
            .map(json -> json != null ? toPayment(json) : null);
    }

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
            p.setCompletedAt(completedObj instanceof java.time.LocalDateTime ? (java.time.LocalDateTime) completedObj : java.time.LocalDateTime.parse(completedObj.toString()));
        }
        Object createdObj = json.getValue("created_at");
        if (createdObj != null) {
            p.setCreatedAt(createdObj instanceof java.time.LocalDateTime ? (java.time.LocalDateTime) createdObj : java.time.LocalDateTime.parse(createdObj.toString()));
        }
        Object updatedObj = json.getValue("updated_at");
        if (updatedObj != null) {
            p.setUpdatedAt(updatedObj instanceof java.time.LocalDateTime ? (java.time.LocalDateTime) updatedObj : java.time.LocalDateTime.parse(updatedObj.toString()));
        }
        // JOIN fields
        Object orderTotalObj = json.getValue("order_total");
        p.setOrderTotal(orderTotalObj != null ? new BigDecimal(orderTotalObj.toString()) : null);
        p.setOrderStatus(json.getString("order_status"));
        p.setUserName(json.getString("user_name"));
        return p;
    }

    private Future<Void> confirmStockAndLedger(Long orderId) {
        return orderRepo.findItemsByOrderIdForTx(orderId)
            .compose(this::confirmStockLoop);
    }

    private Future<Void> confirmStockLoop(List<OrderItem> items) {
        return confirmStockLoopImpl(items, 0);
    }

    private Future<Void> confirmStockLoopImpl(List<OrderItem> items, int index) {
        if (index >= items.size()) return Future.succeededFuture();
        OrderItem item = items.get(index);
        Long productId = item.getProductId();

        return productRepo.findByIdForUpdate(productId)
            .compose(product -> {
                int stockAfter = product.getStock() != null ? product.getStock() : 0;
                return invTxRepo.recordRestoration(productId, item.getOrderId(),
                    0, stockAfter, stockAfter,
                    "Payment confirmed for order " + item.getOrderId() +
                        ": stock already deducted at creation");
            })
            .compose(v -> confirmStockLoopImpl(items, index + 1));
    }
}
