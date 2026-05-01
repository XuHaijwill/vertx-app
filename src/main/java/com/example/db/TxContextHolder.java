package com.example.db;

import io.vertx.core.Context;
import io.vertx.core.Vertx;

/**
 * TxContextHolder — binds a TransactionContext to the current Vert.x Context.
 *
 * <p>Provides "ambient" transaction context without explicit parameter passing.
 * When a {@link com.example.db.DatabaseVerticle#withTransaction} block starts,
 * it calls {@link #bind(TransactionContext)}. All Repository methods then call
 * {@link #current()} to retrieve it — no explicit tx parameter needed.
 *
 * <p>Thread-safety note: Vert.x guarantees all code within a single event-loop
 * turn runs on the same thread. As long as the entire transaction (from bind
 * to unbind) stays within one Vert.x event-loop tick, the Context-local storage
 * is safe.
 *
 * <p>Usage:
 * <pre>
 * // Inside DatabaseVerticle.withTransaction callback:
 * TxContextHolder.bind(txContext);
 * try {
 *     return doBusinessLogic();  // Repository calls TxContextHolder.current()
 * } finally {
 *     TxContextHolder.unbind();
 * }
 *
 * // Inside Repository:
 * TransactionContext tx = TxContextHolder.current();
 * if (tx != null) {
 *     return doInTransaction(tx);
 * } else {
 *     return doInPool();
 * }
 * </pre>
 */
public class TxContextHolder {

    /** Key used to store/retrieve TransactionContext from Vert.x Context local data */
    private static final String TX_KEY = "vertx.tx.context";

    /** The bound context — set by bind(), cleared by unbind() */
    private static TransactionContext bound;

    private TxContextHolder() {}

    /**
     * Bind a TransactionContext to the current Vert.x Context.
     * MUST be called before entering a transaction.
     *
     * <p>Calling {@link #current()} from any code that runs within the same
     * Vert.x event-loop tick will return this context.
     *
     * @param ctx the TransactionContext created by DatabaseVerticle.withTransaction
     * @throws IllegalStateException if already bound (nested transactions not yet supported)
     */
    public static void bind(TransactionContext ctx) {
        if (bound != null) {
            throw new IllegalStateException(
                "Transaction already active (nested transactions not supported yet). " +
                "Current: " + bound + ", new: " + ctx);
        }
        bound = ctx;
        // Also store in Vert.x Context for debugging / inspection
        Context vertxContext = Vertx.currentContext();
        if (vertxContext != null) {
            vertxContext.put(TX_KEY, ctx);
        }
    }

    /**
     * Return the currently active TransactionContext, or null if not in a transaction.
     *
     * <p>Repository methods call this to decide whether to use a pooled connection
     * (tx == null) or the transaction connection (tx != null).
     *
     * @return the bound TransactionContext, or null if no active transaction
     */
    public static TransactionContext current() {
        return bound;
    }

    /**
     * Unbind and clear the current TransactionContext.
     * MUST be called when the transaction completes (commit or rollback).
     */
    public static void unbind() {
        bound = null;
        Context vertxContext = Vertx.currentContext();
        if (vertxContext != null) {
            vertxContext.remove(TX_KEY);
        }
    }

    /**
     * Check whether a transaction is currently active.
     *
     * @return true if {@link #current()} would return a non-null context
     */
    public static boolean isActive() {
        return bound != null;
    }
}
