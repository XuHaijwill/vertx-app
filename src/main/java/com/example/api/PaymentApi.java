package com.example.api;

import com.example.core.BusinessException;
import com.example.service.PaymentService;
import com.example.service.impl.PaymentServiceImpl;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * Payment API — /api/payments
 *
 * Demonstrates multi-table transaction endpoints:
 *   POST   /api/payments/pay              → process payment (5-table transaction)
 *   POST   /api/payments/:id/refund       → refund payment  (4-table transaction)
 *   GET    /api/payments                 → list payments (by status filter)
 *   GET    /api/payments/:id             → get payment detail
 *   GET    /api/payments/order/:orderId → get payment by order ID
 *   GET    /api/payments/user/:userId    → payments by user
 */
public class PaymentApi extends BaseApi {

    private final PaymentService paymentService;

    public PaymentApi(Vertx vertx) {
        super(vertx);
        this.paymentService = new PaymentServiceImpl(vertx);
    }

    @Override
    public void registerRoutes(Router router, String contextPath) {
        router.post(contextPath + "/api/payments/pay").handler(this::processPayment);
        router.post(contextPath + "/api/payments/:id/refund").handler(this::refundPayment);
        router.get(contextPath + "/api/payments").handler(this::listPayments);
        router.get(contextPath + "/api/payments/:id").handler(this::getPayment);
        router.get(contextPath + "/api/payments/order/:orderId").handler(this::getByOrderId);
        router.get(contextPath + "/api/payments/user/:userId").handler(this::listByUser);
    }

    // ================================================================
    // Transactional endpoints
    // ================================================================

    /**
     * POST /api/payments/pay
     * Body: { "orderId": 1, "method": "balance" }
     *
     * Executes processPayment — a 5-table ACID transaction:
     *   payments + orders + users + products + inventory_transactions
     */
    private void processPayment(RoutingContext ctx) {
        JsonObject body = bodyJson(ctx);
        if (body == null) {
            badRequest(ctx, "Request body is required");
            return;
        }
        if (body.getLong("orderId") == null) {
            badRequest(ctx, "orderId is required");
            return;
        }
        respondCreated(ctx, paymentService.processPayment(body));
    }

    /**
     * POST /api/payments/:id/refund
     *
     * Executes refundPayment — a 4-table ACID transaction:
     *   payments + users (balance restore) + orders + inventory_transactions
     */
    private void refundPayment(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) {
            badRequest(ctx, "Invalid payment ID");
            return;
        }
        respond(ctx, paymentService.refundPayment(id));
    }

    // ================================================================
    // Read-only endpoints
    // ================================================================

    /**
     * GET /api/payments?status=completed
     */
    private void listPayments(RoutingContext ctx) {
        String status = queryStr(ctx, "status");
        if (status != null && !status.isBlank()) {
            respond(ctx, paymentService.findByStatus(status));
        } else {
            respond(ctx, paymentService.findByStatus("pending")); // default to pending
        }
    }

    /**
     * GET /api/payments/:id
     */
    private void getPayment(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) {
            badRequest(ctx, "Invalid payment ID");
            return;
        }
        respond(ctx, paymentService.findById(id));
    }

    /**
     * GET /api/payments/order/:orderId
     */
    private void getByOrderId(RoutingContext ctx) {
        Long orderId = parseId(ctx.pathParam("orderId"));
        if (orderId == null) {
            badRequest(ctx, "Invalid order ID");
            return;
        }
        respond(ctx, paymentService.findByOrderId(orderId));
    }

    /**
     * GET /api/payments/user/:userId
     */
    private void listByUser(RoutingContext ctx) {
        Long userId = parseId(ctx.pathParam("userId"));
        if (userId == null) {
            badRequest(ctx, "Invalid user ID");
            return;
        }
        respond(ctx, paymentService.findByUserId(userId));
    }
}
