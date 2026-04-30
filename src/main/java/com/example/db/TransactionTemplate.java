package com.example.db;

import io.vertx.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * TransactionTemplate — declarative transaction execution engine.
 *
 * <p>Wraps a block of async operations in a transaction:
 * <ol>
 *   <li>Opens transaction via {@link DatabaseVerticle#withTransaction}</li>
 *   <li>Binds {@link TransactionContext} to {@link TxContextHolder}</li>
 *   <li>Executes the user-provided block</li>
 *   <li>Commits or rolls back</li>
 *   <li>Unbinds the context</li>
 * </ol>
 *
 * <p>Usage — recommended via {@link #wrap}:
 * <pre>
 * {@literal @}Transactional(timeoutMs = 60_000)
 * public Future&lt;JsonObject&gt; createOrder(JsonObject order) {
 *     return template.wrap(tx -&gt;
 *         userRepo.findByIdForUpdate(userId)
 *             .compose(user -&gt; orderRepo.insertOrder(userId, total, remark))
 *             .compose(orderId -&gt; productRepo.deductStock(productId, qty, orderId))
 *             .map(this::buildResult)
 *     );
 * }
 * </pre>
 *
 * <p>Alternative: extend this class and override {@link #executeInTx(TransactionContext)}.
 * Subclasses can then use {@code super.executeInTx(txCtx)} inside the Future chain
 * and the transaction boundary is handled automatically.
 *
 * <p>Dependency injection pattern — inject TransactionTemplate into your Service:
 * <pre>
 * public class OrderService {
 *     private final TransactionTemplate tx;
 *
 *     public OrderService(Vertx vertx, ...other deps...) {
 *         this.tx = new TransactionTemplate(vertx);
 *     }
 * }
 * </pre>
 *
 * @see Transactional
 * @see TxContextHolder
 */
public class TransactionTemplate {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionTemplate.class);

    protected final Vertx vertx;

    /**
     * Create a TransactionTemplate bound to the given Vert.x instance.
     *
     * @param vertx the Vert.x instance (used to access the DB pool)
     */
    public TransactionTemplate(Vertx vertx) {
        this.vertx = vertx;
    }

    /**
     * Execute a block of operations within a transaction (using default timeout).
     *
     * @param block the async operations to execute — receives the transaction context
     *              via {@link TxContextHolder#current()} (already bound when this runs)
     * @param <T>   result type
     * @return a Future that completes with the block's result or fails on rollback
     */
    public <T> Future<T> wrap(Function<TransactionContext, Future<T>> block) {
        return wrap(block, 30_000);
    }

    /**
     * Execute a block of operations within a transaction with custom timeout.
     *
     * @param block     the async operations
     * @param timeoutMs transaction timeout in milliseconds
     * @param <T>       result type
     * @return a Future that completes with the block's result or fails on rollback
     */
    public <T> Future<T> wrap(Function<TransactionContext, Future<T>> block, int timeoutMs) {
        // Verify we have a DB pool
        if (DatabaseVerticle.getPool(vertx) == null) {
            return Future.failedFuture("Database not available (demo mode)");
        }

        return DatabaseVerticle.withTransaction(vertx, txCtx -> {
            // Bind context so Repository methods can find it via TxContextHolder.current()
            TxContextHolder.bind(txCtx);
            try {
                return block.apply(txCtx);
            } catch (Exception e) {
                return Future.failedFuture(e);
            }
            // Note: unbind() is called inside DatabaseVerticle.withTransaction's
            // onComplete callback, so it runs after the block's Future resolves.
        }, timeoutMs)
        // After the withTransaction block completes (committed or rolled back),
        // ALWAYS unbind — even on unexpected error paths
        .andThen(ar -> {
            if (TxContextHolder.isActive()) {
                // Safety net: withTransaction should already call unbind, but clear
                // any orphaned context just in case.
                String outcome = ar.succeeded() ? "OK" : "FAILED(" + ar.cause().getMessage() + ")";
                LOG.warn("[TX-TEMPLATE] Unbind safety net triggered — context still active after TX {}. " +
                    "Clearing. Result: {}", outcome, outcome);
                TxContextHolder.unbind();
            }
        });
    }

    /**
     * Convenience: execute with the timeout specified in a {@link Transactional} annotation.
     *
     * @param block     async operations
     * @param anno      the annotation carrying timeoutMs
     * @param <T>       result type
     * @return Future with block result
     */
    public <T> Future<T> wrap(Function<TransactionContext, Future<T>> block, Transactional anno) {
        return wrap(block, anno.timeoutMs());
    }
}
