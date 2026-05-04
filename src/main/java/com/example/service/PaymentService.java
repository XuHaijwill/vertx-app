package com.example.service;

import com.example.core.PageResult;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.List;

/**
 * Payment Service Interface
 */
public interface PaymentService {

    /**
     * Process a payment for an order - 5 tables in one transaction
     */
    Future<JsonObject> processPayment(JsonObject request);

    /**
     * Refund a completed payment - 4 tables in one transaction
     */
    Future<JsonObject> refundPayment(Long paymentId);

    /**
     * Find payment by ID
     */
    Future<JsonObject> findById(Long id);

    /**
     * Find payment by order ID
     */
    Future<JsonObject> findByOrderId(Long orderId);

    /**
     * Find payments by user ID
     */
    Future<List<JsonObject>> findByUserId(Long userId);

    /**
     * Find payments by status
     */
    Future<List<JsonObject>> findByStatus(String status);
}
