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

    /** Key used to store/retrieve TransactionContext from Vert.x Context */
    private static final String TX_KEY = "vertx.tx.context";

    private TxContextHolder() {}

    /**
     * Bind a TransactionContext to the current Vert.x Context.
     * MUST be called before entering a transaction.
     *
     * @param ctx the TransactionContext created by DatabaseVerticle.withTransaction
     * @throws IllegalStateException if already bound (nested transactions not yet supported)
     */
    public static void bind(TransactionContext ctx) {
        Context vertxContext = Vertx.currentContext();
        if (vertxContext != null) {
            TransactionContext existing = (TransactionContext) vertxContext.get(TX_KEY);
            if (existing != null) {
                throw new IllegalStateException(
                    "Transaction already active (nested transactions not supported yet). " +
                    "Current: " + existing + ", new: " + ctx);
            }
            vertxContext.put(TX_KEY, ctx);
        } else {
            throw new IllegalStateException(
                "Cannot bind transaction: no Vert.x Context available");
        }
    }

    /**
     * Return the currently active TransactionContext, or null if not in a transaction.
     */
    public static TransactionContext current() {
        Context vertxContext = Vertx.currentContext();
        if (vertxContext != null) {
            return (TransactionContext) vertxContext.get(TX_KEY);
        }
        return null;
    }

    /**
     * Unbind and clear the current TransactionContext.
     * MUST be called when the transaction completes (commit or rollback).
     */
    public static void unbind() {
        Context vertxContext = Vertx.currentContext();
        if (vertxContext != null) {
            vertxContext.remove(TX_KEY);
        }
    }

    /**
     * Check whether a transaction is currently active.
     */
    public static boolean isActive() {
        return current() != null;
    }
}
