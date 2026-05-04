package com.example.service;

import com.example.core.PageResult;
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
    Future<JsonObject> createOrder(JsonObject order);

    /**
     * Cancel an order - restore stock + update status in ONE transaction
     */
    Future<JsonObject> cancelOrder(Long orderId);

    /**
     * Find all orders
     */
    Future<List<JsonObject>> findAll();

    /**
     * Find order by ID
     */
    Future<JsonObject> findById(Long id);

    /**
     * Find orders by user ID
     */
    Future<List<JsonObject>> findByUserId(Long userId);

    /**
     * Find orders with pagination
     */
    Future<PageResult<JsonObject>> findPaginated(int page, int size);
}
