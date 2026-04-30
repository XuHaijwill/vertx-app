package com.example.api;

import com.example.core.BusinessException;
import com.example.service.OrderService;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * Order API — /api/orders
 *
 * Demonstrates multi-table transaction endpoints:
 *   POST   /api/orders          → create order (transaction: order + items + stock)
 *   PUT    /api/orders/:id/cancel → cancel order (transaction: restore stock + status)
 *   GET    /api/orders          → list orders (paginated)
 *   GET    /api/orders/:id      → get order detail
 *   GET    /api/orders/user/:userId → orders by user
 */
public class OrderApi extends BaseApi {

    private final OrderService orderService;

    public OrderApi(Vertx vertx) {
        super(vertx);
        this.orderService = new OrderService(vertx);
    }

    @Override
    public void registerRoutes(Router router, String contextPath) {
        router.post(contextPath + "/api/orders").handler(this::createOrder);
        router.put(contextPath + "/api/orders/:id/cancel").handler(this::cancelOrder);
        router.get(contextPath + "/api/orders").handler(this::listOrders);
        router.get(contextPath + "/api/orders/:id").handler(this::getOrder);
        router.get(contextPath + "/api/orders/user/:userId").handler(this::listByUser);
    }

    /**
     * POST /api/orders
     * Body: { "userId": 1, "remark": "optional", "items": [{"productId": 1, "quantity": 2, "price": 99.9}] }
     */
    private void createOrder(RoutingContext ctx) {
        JsonObject body = bodyJson(ctx);
        if (body == null) {
            badRequest(ctx, "Request body is required");
            return;
        }
        respondCreated(ctx, orderService.createOrder(body));
    }

    /**
     * PUT /api/orders/:id/cancel
     */
    private void cancelOrder(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) {
            badRequest(ctx, "Invalid order ID");
            return;
        }
        respond(ctx, orderService.cancelOrder(id));
    }

    /**
     * GET /api/orders?page=1&size=20
     */
    private void listOrders(RoutingContext ctx) {
        int page = queryIntClamped(ctx, "page", 1, 1, 1000);
        int size = queryIntClamped(ctx, "size", 20, 1, 100);
        respondPaginated(ctx, orderService.findPaginated(page, size));
    }

    /**
     * GET /api/orders/:id
     */
    private void getOrder(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) {
            badRequest(ctx, "Invalid order ID");
            return;
        }
        respond(ctx, orderService.findById(id));
    }

    /**
     * GET /api/orders/user/:userId
     */
    private void listByUser(RoutingContext ctx) {
        Long userId = parseId(ctx.pathParam("userId"));
        if (userId == null) {
            badRequest(ctx, "Invalid user ID");
            return;
        }
        respond(ctx, orderService.findByUserId(userId));
    }
}
