package com.example.db;

import com.example.core.Config;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.SslMode;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Database Verticle — PostgreSQL connection pool manager.
 *
 * Config keys (set by App.java via Config.load()):
 *   db.host, db.port, db.database, db.user, db.password, db.pool-size, db.ssl
 *
 * Demo mode: if db.host is missing, runs without DB (sample in-memory data).
 */
public class DatabaseVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseVerticle.class);
    public static final String DB_POOL = "db.pool";
    /** Default transaction timeout: 30 seconds */
    public static final int DEFAULT_TX_TIMEOUT_MS = 30_000;

    private Pool pool;

    @Override
    public void start(Promise<Void> startPromise) {
        JsonObject cfg = config();
        String host     = Config.getDbHost(cfg);
        int    port     = Config.getDbPort(cfg);
        String database = Config.getDbDatabase(cfg);
        String user     = Config.getDbUser(cfg);
        String password = Config.getDbPassword(cfg);
        int    poolSize = Config.getDbPoolSize(cfg);
        boolean ssl     = Config.getDbSsl(cfg);

        LOG.info("[DB] host={}:{}/{} user={} poolSize={} ssl={}",
            host, port, database, user, poolSize, ssl);

        boolean demoMode = host.equals("localhost") && password.equals("postgres");
        if (demoMode) {
            LOG.warn("[DB] No DB configured — running DEMO MODE");
            startPromise.complete();
            return;
        }

        PgConnectOptions connectOptions = new PgConnectOptions()
            .setHost(host).setPort(port).setDatabase(database)
            .setUser(user).setPassword(password)
            .setSslMode(ssl ? SslMode.REQUIRE : SslMode.DISABLE);

        PoolOptions poolOpts = new PoolOptions()
            .setMaxSize(poolSize).setName("vertx-app-pool")
            .setEventLoopSize(4).setShared(true);

        pool = PgBuilder.pool()
            .with(poolOpts).connectingTo(connectOptions).using(vertx).build();

        runMigrations(host, port, database, user, password, ssl)
            .compose(v -> {
                context.put(DB_POOL, pool);
                String connUri = String.format("postgresql://%s:%s@%s:%d/%s", user, password, host, port, database);
                vertx.sharedData().getLocalMap("db").put("conn-uri", connUri);
                LOG.info("[DB] PostgreSQL connected");
                startPromise.complete();
                return Future.succeededFuture();
            })
            .onFailure(err -> {
                LOG.error("[DB] Failed to initialize database", err);
                LOG.warn("[DB] Running in DEMO MODE");
                startPromise.complete();
            });
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        if (pool != null) {
            pool.close()
                .onSuccess(v -> { LOG.info("[DB] Pool closed"); stopPromise.complete(); })
                .onFailure(stopPromise::fail);
        } else {
            stopPromise.complete();
        }
    }

    // ================================================================
    // Pool access
    // ================================================================

    public static Pool getPool(Vertx vertx) {
        if (vertx == null) return null;
        Pool p = (Pool) vertx.getOrCreateContext().get(DB_POOL);
        if (p != null) return p;
        var map = vertx.sharedData().getLocalMap("db");
        String connUri = map != null ? (String) map.get("conn-uri") : null;
        if (connUri != null) {
            try {
                PgConnectOptions opts = PgConnectOptions.fromUri(connUri);
                PoolOptions poolOpts = new PoolOptions()
                    .setMaxSize(10).setName("vertx-app-pool").setShared(true);
                return PgBuilder.pool()
                    .with(poolOpts).connectingTo(opts).using(vertx).build();
            } catch (Exception e) {
                LOG.warn("[DB] Failed to rebuild shared pool from URI", e);
            }
        }
        return null;
    }

    // ================================================================
    // Connection helpers (for audit logging and other non-transactional use)
    // ================================================================

    /**
     * Acquire a connection from the pool, use it, then automatically close it.
     *
     * <p>This is the recommended way to run single-statement operations
     * without the overhead of managing connection lifecycle manually.
     *
     * <p>Usage:
     * <pre>
     * return DatabaseVerticle.withConnection(vertx)
     *     .compose(conn -> conn.query(sql).execute(params)
     *         .eventually(v -> conn.close())
     *         .map(rows -> toJsonList(rows));
     * </pre>
     *
     * @param vertx  Vert.x instance
     * @param <T>    result type
     * @return Future that completes with the result, or fails if pool unavailable
     */
    public static <T> Future<SqlConnection> withConnection(Vertx vertx) {
        Pool pool = getPool(vertx);
        if (pool == null) return Future.failedFuture("Database not available (demo mode)");
        return pool.getConnection();
    }

    // ================================================================
    // Pool-based queries (standalone, no transaction)
    // ================================================================

    public static Future<RowSet<Row>> query(Vertx vertx, String sql, Tuple params) {
        Pool pool = getPool(vertx);
        if (pool == null) return Future.failedFuture("Database not available (demo mode)");
        return pool.preparedQuery(sql).execute(params);
    }

    public static Future<RowSet<Row>> query(Vertx vertx, String sql) {
        return query(vertx, sql, Tuple.tuple());
    }

    // ================================================================
    // Transaction helpers — TransactionContext-based (preferred)
    // ================================================================

    /**
     * Execute operations within a transaction using TransactionContext (preferred API).
     *
     * <p>This is the recommended way to write multi-table transactions. All Repository
     * transaction methods accept {@link TransactionContext} for a uniform API.
     *
     * <p>Usage:
     * <pre>
     * return DatabaseVerticle.withTransaction(vertx, tx -&gt;
     *     orderRepo.findByIdForUpdate(tx, orderId)
     *         .compose(order -&gt; paymentRepo.insertPayment(tx, orderId, userId, amount, ...))
     *         .compose(pid -&gt; orderRepo.updateStatusInTx(tx, orderId, "completed"))
     * );
     * </pre>
     *
     * <p>Timeout (default 30s): if the transaction exceeds this budget,
     * the connection is force-closed and the transaction is rolled back.
     *
     * @param vertx  Vert.x instance
     * @param block  Lambda receiving TransactionContext; use {@code tx.conn()} for SQL
     * @param <T>    Result type
     * @return Future that completes with the block's result, or fails on any error
     */
    public static <T> Future<T> withTransaction(Vertx vertx,
                                                  Function<TransactionContext, Future<T>> block) {
        return withTransaction(vertx, block, DEFAULT_TX_TIMEOUT_MS);
    }

    /**
     * Execute operations within a transaction using TransactionContext with custom timeout.
     *
     * @param vertx     Vert.x instance
     * @param block     Lambda receiving TransactionContext
     * @param timeoutMs Timeout in milliseconds; forces connection close on expiry
     */
    public static <T> Future<T> withTransaction(Vertx vertx,
                                                  Function<TransactionContext, Future<T>> block,
                                                  int timeoutMs) {
        Pool pool = getPool(vertx);
        if (pool == null) return Future.failedFuture("Database not available (demo mode)");

        LOG.debug("[TX] Opening (TransactionContext), timeout={}ms", timeoutMs);
        long startTime = System.currentTimeMillis();

        return pool.getConnection()
            .compose(conn -> {
                // TransactionContext wraps the connection for uniform API
                TransactionContext txCtx = new TransactionContext(conn, timeoutMs);

                // Timeout guard — force close connection if tx takes too long
                long timerId = vertx.setTimer(timeoutMs, id -> {
                    LOG.warn("[TX] Timeout after {}ms ({} ops) — force closing",
                        timeoutMs, txCtx.operationCount());
                    conn.close();
                });

                return conn.begin()
                    .compose(tx -> {
                        Future<T> result;
                        try {
                            result = block.apply(txCtx);
                        } catch (Exception e) {
                            result = Future.failedFuture(e);
                        }

                        return result
                            .compose(
                                value -> {
                                    if (txCtx.isRollbackOnly()) {
                                        LOG.debug("[TX] Marked rollback-only — skipping commit");
                                        return tx.rollback()
                                            .onComplete(rbAr -> {
                                                vertx.cancelTimer(timerId);
                                                conn.close();
                                                long ms = System.currentTimeMillis() - startTime;
                                                LOG.info("[TX] Rollback-only in {}ms ({})", ms, txCtx);
                                                TxContextHolder.unbind();  // always unbind on completion
                                            })
                                            .compose(v -> Future.<T>failedFuture(
                                                new RuntimeException("Transaction marked rollback-only")));
                                    }
                                    return tx.commit()
                                        .onComplete(ar -> {
                                            vertx.cancelTimer(timerId);
                                            conn.close();
                                            long ms = System.currentTimeMillis() - startTime;
                                            if (ar.succeeded()) {
                                                LOG.info("[TX] Committed {}ms ({} ops) — {}",
                                                    ms, txCtx.operationCount(), txCtx);
                                            } else {
                                                LOG.error("[TX] Commit failed {}ms: {}",
                                                    ms, ar.cause().getMessage());
                                            }
                                            TxContextHolder.unbind();  // always unbind on completion
                                        })
                                        .map(value);
                                },
                                err -> tx.rollback()
                                    .onComplete(rbAr -> {
                                        vertx.cancelTimer(timerId);
                                        conn.close();
                                        long ms = System.currentTimeMillis() - startTime;
                                        if (rbAr.succeeded()) {
                                            LOG.warn("[TX] Rolled back {}ms ({}) — {}",
                                                ms, txCtx.operationCount(), err.getMessage());
                                        } else {
                                            LOG.error("[TX] Rollback failed {}ms: {}",
                                                ms, rbAr.cause().getMessage());
                                        }
                                        TxContextHolder.unbind();  // always unbind on completion
                                    })
                                    .compose(v -> Future.<T>failedFuture(err))
                            );
                    })
                    .onFailure(f -> {
                        vertx.cancelTimer(timerId);
                        conn.close();
                        TxContextHolder.unbind();  // unbind even on connection-level failure
                    });
            });
    }

    // ================================================================
    // Transaction-scoped query helpers (work on SqlConnection)
    // All are static — call from inside any withTransaction callback
    // ================================================================

    /** Execute a prepared query inside a transaction (returns RowSet). */
    public static Future<RowSet<Row>> queryInTx(SqlConnection conn, String sql, Tuple params) {
        return conn.preparedQuery(sql).execute(params);
    }

    public static Future<RowSet<Row>> queryInTx(SqlConnection conn, String sql) {
        return queryInTx(conn, sql, Tuple.tuple());
    }

    /** Execute a prepared query inside a transaction (returns JsonObject list). */
    public static Future<List<JsonObject>> queryListInTx(SqlConnection conn, String sql, Tuple params) {
        return queryInTx(conn, sql, params).map(DatabaseVerticle::toJsonList);
    }

    public static Future<List<JsonObject>> queryListInTx(SqlConnection conn, String sql) {
        return queryListInTx(conn, sql, Tuple.tuple());
    }

    /**
     * Execute a prepared query inside a transaction (returns single JsonObject or null).
     * Use when you expect 0 or 1 result row (e.g., SELECT ... FOR UPDATE).
     */
    public static Future<JsonObject> queryOneInTx(SqlConnection conn, String sql, Tuple params) {
        return queryInTx(conn, sql, params)
            .map(rows -> {
                List<JsonObject> list = toJsonList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    public static Future<JsonObject> queryOneInTx(SqlConnection conn, String sql) {
        return queryOneInTx(conn, sql, Tuple.tuple());
    }

    /**
     * Execute UPDATE/INSERT/DELETE inside a transaction.
     * @return affected row count
     */
    public static Future<Long> updateInTx(SqlConnection conn, String sql, Tuple params) {
        return queryInTx(conn, sql, params).map(rows -> (long) rows.rowCount());
    }

    // ================================================================
    // JSON conversion helpers
    // ================================================================

    public static List<JsonObject> toJsonList(RowSet<Row> rows) {
        return StreamSupport.stream(rows.spliterator(), false)
            .map(row -> {
                JsonObject obj = new JsonObject();
                for (int i = 0; i < row.size(); i++) {
                    obj.put(row.getColumnName(i), convertValue(row.getValue(i)));
                }
                return obj;
            })
            .collect(Collectors.toList());
    }

    public static JsonObject toJson(Row row) {
        JsonObject obj = new JsonObject();
        for (int i = 0; i < row.size(); i++) {
            obj.put(row.getColumnName(i), convertValue(row.getValue(i)));
        }
        return obj;
    }

    private static Object convertValue(Object value) {
        if (value == null) return null;
        if (value instanceof java.time.LocalDate)    return value.toString();
        if (value instanceof java.time.LocalDateTime) return value.toString();
        if (value instanceof java.time.OffsetDateTime) return value.toString();
        return value;
    }

    // ================================================================
    // Flyway migration runner
    // ================================================================

    private Future<Void> runMigrations(String host, int port, String database,
                                         String user, String password, boolean ssl) {
        Promise<Void> p = Promise.promise();
        vertx.executeBlocking((java.util.concurrent.Callable<Void>) () -> {
            FlywayMigration.migrateSync(host, port, database, user, password, ssl);
            return null;
        }).onSuccess(v -> {
            LOG.info("[DB] Migrations complete");
            p.complete();
        }).onFailure(err -> {
            LOG.warn("[DB] Migration warning (non-fatal): {}",
                err != null ? err.getMessage() : "unknown");
            p.complete();
        });
        return p.future();
    }

    // ================================================================
    // SQL helpers (NOT for table creation — use Flyway migrations)
    // ================================================================

    private Future<Void> executeSQL(String sql) {
        Promise<Void> p = Promise.promise();
        if (pool == null) { p.complete(); return p.future(); }
        pool.query(sql).execute()
            .onSuccess(rows -> { LOG.debug("[DB] SQL OK: {} rows", rows.rowCount()); p.complete(); })
            .onFailure(err -> {
                LOG.warn("[DB] SQL failed (non-fatal): {}", err.getMessage());
                p.complete();
            });
        return p.future();
    }
}
