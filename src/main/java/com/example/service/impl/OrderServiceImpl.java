package com.example.service.impl;
import com.example.db.Transactional;

import com.example.core.BusinessException;
import com.example.core.PageResult;


import com.example.entity.Order;
import com.example.entity.OrderItem;
import com.example.repository.InventoryTransactionRepository;
import com.example.repository.OrderRepository;
import com.example.repository.ProductRepository;
import com.example.repository.UserRepository;
import com.example.service.OrderService;
import com.example.entity.Product;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

/**
 * Order Service Implementation — demonstrates declarative transaction patterns.
 */
public class OrderServiceImpl  implements OrderService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepo;
    private final ProductRepository productRepo;
    private final InventoryTransactionRepository invTxRepo;
    private final UserRepository userRepo;
    public OrderServiceImpl(Vertx vertx) {
        this.orderRepo = new OrderRepository(vertx);
        this.productRepo = new ProductRepository(vertx);
        this.invTxRepo = new InventoryTransactionRepository(vertx);
        this.userRepo = new UserRepository(vertx);
    }
    @Override
    @Transactional(timeoutMs = 60_000)
    public Future<Order> createOrder(JsonObject order) {

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

        // Pre-calculate total
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

        return userRepo.findByIdForUpdate(userId)
            .compose(user -> orderRepo.insertOrder(userId, orderTotal, remark))
            .compose(orderId -> insertItemsSequence(orderId, items, 0).map(orderId))
            .compose(orderId -> deductStockSequence(orderId, items, 0).map(orderId))
            .compose(orderId -> Future.succeededFuture(orderId))
            .compose(orderId -> orderRepo.findById(orderId))
            .onSuccess(result -> LOG.info("[ORDER] Created id={}, userId={}, total={}, items={}",
                result.getId(), userId, orderTotal, itemCount))
            .onFailure(err -> LOG.error("[ORDER] Create failed: {}", err.getMessage()));
    }

    @Override
    @Transactional(timeoutMs = 30_000)
    public Future<Order> cancelOrder(Long orderId) {

        return orderRepo.findByIdForUpdate(orderId)
            .compose(order -> {
                String status = order.getStatus();
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
            .compose(order -> orderRepo.findItemsByOrderIdForTx(orderId)
                .compose(items -> restoreStockSequence(orderId, items, 0).map(order)))
            .compose(order -> orderRepo.updateStatus(orderId, "cancelled").map(order))
            .compose(order -> Future.succeededFuture(order))
            .onSuccess(result -> LOG.info("[ORDER] Cancelled id={}", orderId))
            .onFailure(err -> LOG.error("[ORDER] Cancel failed: {}", err.getMessage()));
    }

    @Override
    public Future<List<Order>> findAll() {
        return orderRepo.findAll();
    }

    @Override
    public Future<Order> findById(Long id) {
        return orderRepo.findById(id)
            .map(order -> {
                if (order == null) throw BusinessException.notFound("Order");
                return order;
            });
    }

    @Override
    public Future<List<Order>> findByUserId(Long userId) {
        return orderRepo.findByUserId(userId);
    }

    @Override
    public Future<PageResult<Order>> findPaginated(int page, int size) {
        return orderRepo.count()
            .compose(total -> orderRepo.findPaginated(page, size)
                .map(list -> new PageResult<>(list, total, page, size)));
    }

    private Future<Void> insertItemsSequence(Long orderId, JsonArray items, int index) {
        if (index >= items.size()) return Future.succeededFuture();
        JsonObject item = items.getJsonObject(index);
        return orderRepo.insertItem(orderId, item.getLong("productId"),
                item.getInteger("quantity", 1),
                BigDecimal.valueOf(item.getDouble("price", 0.0)))
            .compose(v -> insertItemsSequence(orderId, items, index + 1));
    }

    private Future<Void> deductStockSequence(Long orderId, JsonArray items, int index) {
        if (index >= items.size()) return Future.succeededFuture();
        JsonObject item = items.getJsonObject(index);
        Long productId = item.getLong("productId");
        int quantity = item.getInteger("quantity", 1);

        return productRepo.findByIdForUpdate(productId)
            .compose(product -> {
                int before = product.getStock() != null ? product.getStock() : 0;
                return productRepo.deductStock(productId, quantity, orderId)
                    .compose(after -> invTxRepo.recordDeduction(productId, orderId,
                        -quantity, before, after,
                        "Order " + orderId + ": deduct " + quantity + " unit(s)"))
                    .mapEmpty();
            })
            .compose(v -> deductStockSequence(orderId, items, index + 1));
    }

    private Future<Void> restoreStockSequence(Long orderId, List<OrderItem> items, int index) {
        if (index >= items.size()) return Future.succeededFuture();
        OrderItem item = items.get(index);
        Long productId = item.getProductId();
        int quantity = item.getQuantity();

        return productRepo.findByIdForUpdate(productId)
            .compose(product -> {
                int before = product.getStock() != null ? product.getStock() : 0;
                return productRepo.restoreStock(productId, quantity, orderId)
                    .compose(after -> invTxRepo.recordRestoration(productId, orderId,
                        quantity, before, after,
                        "Order " + orderId + " cancelled: restore " + quantity + " unit(s)"))
                    .mapEmpty();
            })
            .compose(v -> restoreStockSequence(orderId, items, index + 1));
    }
}
