package com.example.service;

import com.example.core.PageResult;
import com.example.entity.Order;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.List;

/**
 * Order Service Interface
 */
public interface OrderService {

    /**
     * Create an order with multiple items - ALL OR NOTHING transaction
     */
    Future<Order> createOrder(JsonObject order);

    /**
     * Cancel an order - restore stock + update status in ONE transaction
     */
    Future<Order> cancelOrder(Long orderId);

    /**
     * Find all orders
     */
    Future<List<Order>> findAll();

    /**
     * Find order by ID
     */
    Future<Order> findById(Long id);

    /**
     * Find orders by user ID
     */
    Future<List<Order>> findByUserId(Long userId);

    /**
     * Find orders with pagination
     */
    Future<PageResult<Order>> findPaginated(int page, int size);
}
