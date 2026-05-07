package com.example.service;

import com.example.entity.Payment;
import com.example.core.PageResult;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.List;

/**
 * Payment Service Interface
 *
 * All methods that mutate data carry @Transactional semantics
 * (handled by TransactionTemplate in the implementation).
 */
public interface PaymentService {

    /**
     * Process a payment for an order — 5 tables in one transaction.
     * Returns enriched Payment (with orderTotal, orderStatus, userName).
     */
    Future<Payment> processPayment(JsonObject request);

    /**
     * Refund a completed payment — 4 tables in one transaction.
     */
    Future<Payment> refundPayment(Long paymentId);

    /**
     * Find payment by ID (with JOIN enrichment).
     */
    Future<Payment> findById(Long id);

    /**
     * Find payment by order ID.
     */
    Future<Payment> findByOrderId(Long orderId);

    /**
     * Find payments by user ID.
     */
    Future<List<Payment>> findByUserId(Long userId);

    /**
     * Find payments by status.
     */
    Future<List<Payment>> findByStatus(String status);
}