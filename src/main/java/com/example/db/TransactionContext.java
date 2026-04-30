package com.example.db;

import io.vertx.sqlclient.SqlConnection;

/**
 * TransactionContext — wraps a transaction-scoped SqlConnection.
 *
 * <p>Created inside {@link DatabaseVerticle#withTransaction} callbacks
 * and passed to Repository methods so they can participate in the same transaction.
 *
 * <p>All Repository transaction methods accept {@code TransactionContext} instead of
 * raw {@code SqlConnection}, providing:
 * <ul>
 *   <li>Uniform API across all Repositories</li>
 *   <li>Operation counting for monitoring/debugging</li>
 *   <li>Elapsed time tracking</li>
 *   <li>Extensibility (nested-tx detection, tracing, etc.)</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * DatabaseVerticle.withTransaction(vertx, tx -&gt;
 *     orderRepo.insertOrder(tx, userId, total, remark)
 *         .compose(orderId -&gt; productRepo.deductStock(tx, productId, qty))
 *         .map(orderId)
 * );
 * </pre>
 */
public class TransactionContext {

    private final SqlConnection conn;
    private final long startedAt;
    private final long timeoutMs;
    private int operationCount;
    private boolean rollbackOnly;

    /**
     * Create a new transaction context.
     *
     * @param conn     the transaction-scoped connection
     * @param timeoutMs the timeout budget (ms) — for logging only
     */
    public TransactionContext(SqlConnection conn, long timeoutMs) {
        this.conn = conn;
        this.startedAt = System.currentTimeMillis();
        this.timeoutMs = timeoutMs;
        this.operationCount = 0;
        this.rollbackOnly = false;
    }

    /** @return the underlying SqlConnection for query execution */
    public SqlConnection conn() {
        return conn;
    }

    /** @return elapsed time since this context was created (ms) */
    public long elapsedMs() {
        return System.currentTimeMillis() - startedAt;
    }

    /** @return configured timeout budget (ms) */
    public long timeoutMs() {
        return timeoutMs;
    }

    /**
     * Mark that an operation was executed inside this transaction.
     * Called automatically by Repository methods.
     *
     * @return this (for fluent chaining)
     */
    public TransactionContext tick() {
        operationCount++;
        return this;
    }

    /** @return number of SQL operations executed within this transaction */
    public int operationCount() {
        return operationCount;
    }

    /**
     * Mark this transaction as rollback-only. The commit step will be skipped
     * and a rollback will be performed instead. Useful when validation fails
     * mid-transaction but you still want to clean up gracefully.
     */
    public void setRollbackOnly() {
        this.rollbackOnly = true;
    }

    /** @return true if this transaction has been marked for rollback */
    public boolean isRollbackOnly() {
        return rollbackOnly;
    }

    /**
     * Check remaining timeout budget and throw if exceeded.
     * Can be called before expensive operations to fail fast.
     *
     * @throws RuntimeException if timeout budget is exhausted
     */
    public void checkTimeout() {
        if (elapsedMs() > timeoutMs * 0.9) {  // 90% threshold
            throw new RuntimeException("Transaction approaching timeout: "
                + elapsedMs() + "ms / " + timeoutMs + "ms ("
                + operationCount + " ops)");
        }
    }

    @Override
    public String toString() {
        return "TransactionContext{ops=" + operationCount
            + ", elapsed=" + elapsedMs() + "ms"
            + ", timeout=" + timeoutMs + "ms"
            + ", rollbackOnly=" + rollbackOnly + "}";
    }
}
