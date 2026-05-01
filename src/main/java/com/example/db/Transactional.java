package com.example.db;

import java.lang.annotation.*;

/**
 * Declarative transaction boundary — marks a method as running inside a transaction.
 *
 * <p>Applied to Service layer methods. When a {@code @Transactional} method is called,
 * the {@link TransactionTemplate} interceptor:
 * <ol>
 *   <li>Opens a new transaction via {@link DatabaseVerticle#withTransaction}</li>
 *   <li>Binds the {@link TransactionContext} to {@link TxContextHolder}</li>
 *   <li>Executes the method body</li>
 *   <li>On success: commits; on failure: rolls back</li>
 *   <li>Unbinds and returns the result</li>
 * </ol>
 *
 * <p>Inside the method body, all Repository calls can omit the explicit
 * {@code TransactionContext} parameter — each Repository auto-detects
 * {@link TxContextHolder#current()} and routes to the correct implementation.
 *
 * <p>Example:
 * <pre>
 * {@literal @}Transactional(timeoutMs = 60_000)
 * public Future&lt;JsonObject&gt; createOrder(JsonObject order) {
 *     // No tx parameter needed!
 *     return userRepo.findByIdForUpdate(userId)
 *         .compose(user -&gt; orderRepo.insertOrder(userId, total, remark))
 *         .compose(orderId -&gt; productRepo.deductStock(productId, qty, orderId))
 *         .map(this::buildResult);
 * }
 * </pre>
 *
 * <p>Timeout behaviour: if the transaction exceeds {@code timeoutMs}, the
 * underlying DB connection is force-closed and the transaction rolls back.
 *
 * @see TransactionTemplate
 * @see TxContextHolder
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Transactional {

    /**
     * Transaction timeout in milliseconds.
     * The transaction will be rolled back if it exceeds this budget.
     *
     * @return timeout in ms (default 30 000)
     */
    int timeoutMs() default 30_000;
}
