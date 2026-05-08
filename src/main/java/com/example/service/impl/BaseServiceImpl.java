package com.example.service.impl;

import com.example.db.AuditLogger;
import com.example.db.DatabaseVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.util.List;

/**
 * Base service implementation providing common infrastructure.
 * <p>
 * All concrete service impls extend this class to eliminate boilerplate:
 * <pre>
 * public class MyServiceImpl extends BaseServiceImpl implements MyService {
 *     public MyServiceImpl(Vertx vertx) {
 *         super(vertx, MyRepository::new);
 *     }
 * }
 * </pre>
 *
 * @param <R> repository type
 */
public abstract class BaseServiceImpl<R> {

    protected final Vertx vertx;
    protected final R     repo;
    protected final AuditLogger audit;
    /** False when the database is unavailable (e.g. tests, startup before MigrateVerticle). */
    protected final boolean dbAvailable;

    protected BaseServiceImpl(Vertx vertx, java.util.function.Function<Vertx, R> repoFactory) {
        this.vertx   = vertx;
        this.repo    = repoFactory.apply(vertx);
        this.audit   = new AuditLogger(vertx);
        this.dbAvailable = DatabaseVerticle.getPool(vertx) != null;
    }

    /**
     * Short-circuit return for when the DB is unavailable.
     * Use at the top of every read method that returns a collection.
     */
    @SuppressWarnings("unchecked")
    protected <T> Future<List<T>> failIfUnavailable() {
        return Future.succeededFuture((List<T>) List.of());
    }

    /**
     * Short-circuit return for when the DB is unavailable (single-item variant).
     */
    @SuppressWarnings("unchecked")
    protected <T> Future<T> failIfUnavailableNull() {
        return Future.succeededFuture((T) null);
    }
}