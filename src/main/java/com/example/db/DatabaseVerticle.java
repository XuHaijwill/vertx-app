package com.example.db;

import com.example.core.Config;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Transaction;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;
import java.util.List;
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

    /** 默认事务超时 30 秒 */
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

        boolean demoMode = (host.equals("localhost") && password.equals("postgres"));

        if (demoMode) {
            LOG.warn("[DB] No DB configured - running DEMO MODE (no database)");
            startPromise.complete();
            return;
        }

        PgConnectOptions connectOptions = new PgConnectOptions()
            .setHost(host)
            .setPort(port)
            .setDatabase(database)
            .setUser(user)
            .setPassword(password)
            .setSslMode(ssl ? io.vertx.pgclient.SslMode.REQUIRE : io.vertx.pgclient.SslMode.DISABLE);

        PoolOptions poolOpts = new PoolOptions()
            .setMaxSize(poolSize)
            .setName("vertx-app-pool")
            .setEventLoopSize(4)
            .setShared(true);

        pool = PgBuilder.pool()
            .with(poolOpts)
            .connectingTo(connectOptions)
            .using(vertx)
            .build();

        LOG.info("[DB] Connecting to PostgreSQL {}:{}/{}", host, port, database);

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
    // Public helpers
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
                    .setMaxSize(10)
                    .setName("vertx-app-pool")
                    .setShared(true);
                return PgBuilder.pool()
                    .with(poolOpts)
                    .connectingTo(opts)
                    .using(vertx)
                    .build();
            } catch (Exception e) {
                LOG.warn("[DB] Failed to rebuild shared pool from URI", e);
            }
        }
        return null;
    }

    public static Future<RowSet<Row>> query(Vertx vertx, String sql, Tuple params) {
        Pool pool = getPool(vertx);
        if (pool == null) {
            return Future.failedFuture("Database not available (demo mode)");
        }
        return pool.preparedQuery(sql).execute(params);
    }

    public static Future<RowSet<Row>> query(Vertx vertx, String sql) {
        return query(vertx, sql, Tuple.tuple());
    }

    // ================================================================
    // Transactional operations
    // ================================================================

    /**
     * Execute database operations within a transaction (default 30s timeout).
     *
     * <p>All operations succeed → auto commit. Any failure → auto rollback.
     * Connection is always returned to the pool after the transaction ends.
     * If the transaction exceeds the timeout, the connection is force-closed.
     *
     * <p>Usage example:
     * <pre>
     * return DatabaseVerticle.withTransaction(vertx, conn ->
     *     conn.preparedQuery("INSERT INTO orders ...").execute(params1)
     *       .compose(r1 -> conn.preparedQuery("INSERT INTO items ...").execute(params2))
     *       .map(r2 -> r2.iterator().next().getLong("id"))
     * );
     * </pre>
     *
     * @param vertx  Vert.x instance
     * @param block  Lambda receiving SqlConnection; queries go through conn, NOT through Pool.
     *               Do NOT call commit/rollback yourself — handled automatically.
     * @param <T>    Result type of the transaction
     * @return Future that completes with the block's result, or fails if any step fails
     */
    public static <T> Future<T> withTransaction(Vertx vertx,
                                                  Function<SqlConnection, Future<T>> block) {
        return withTransaction(vertx, block, DEFAULT_TX_TIMEOUT_MS);
    }

    /**
     * Execute database operations within a transaction (custom timeout).
     *
     * @param vertx     Vert.x instance
     * @param block     Lambda receiving SqlConnection
     * @param timeoutMs Timeout in milliseconds; forces connection close on expiry
     */
    public static <T> Future<T> withTransaction(Vertx vertx,
                                                  Function<SqlConnection, Future<T>> block,
                                                  int timeoutMs) {
        Pool pool = getPool(vertx);
        if (pool == null) {
            return Future.failedFuture("Database not available (demo mode)");
        }

        LOG.debug("[TX] Opening, timeout={}ms", timeoutMs);
        long startTime = System.currentTimeMillis();

        return pool.getConnection()
            .compose(conn -> {
                // Timeout guard — force close connection if tx takes too long
                long timerId = vertx.setTimer(timeoutMs, id -> {
                    LOG.warn("[TX] Timeout after {}ms — force closing connection", timeoutMs);
                    conn.close();
                });

                return conn.begin()
                    .compose(tx -> {
                        Future<T> result;
                        try {
                            result = block.apply(conn);
                        } catch (Exception e) {
                            result = Future.failedFuture(e);
                        }

                        return result
                            .compose(
                                // Success → commit
                                value -> tx.commit()
                                    .onComplete(ar -> {
                                        vertx.cancelTimer(timerId);
                                        conn.close();
                                        long ms = System.currentTimeMillis() - startTime;
                                        if (ar.succeeded()) {
                                            LOG.info("[TX] Committed in {}ms", ms);
                                        } else {
                                            LOG.error("[TX] Commit failed in {}ms: {}", ms, ar.cause().getMessage());
                                        }
                                    })
                                    .map(value),
                                // Failure → rollback
                                err -> tx.rollback()
                                    .onComplete(rbAr -> {
                                        vertx.cancelTimer(timerId);
                                        conn.close();
                                        long ms = System.currentTimeMillis() - startTime;
                                        if (rbAr.succeeded()) {
                                            LOG.warn("[TX] Rolled back in {}ms — {}", ms, err.getMessage());
                                        } else {
                                            LOG.error("[TX] Rollback failed in {}ms: {}", ms, rbAr.cause().getMessage());
                                        }
                                    })
                                    .compose(v -> Future.<T>failedFuture(err))
                            );
                    })
                    .onFailure(f -> {
                        vertx.cancelTimer(timerId);
                        conn.close();
                    });
            });
    }

    // ================================================================
    // Transaction-scoped query helpers
    // ================================================================

    /**
     * Execute a prepared query inside a transaction (returns RowSet).
     */
    public static Future<RowSet<Row>> queryInTx(SqlConnection conn, String sql, Tuple params) {
        return conn.preparedQuery(sql).execute(params);
    }

    public static Future<RowSet<Row>> queryInTx(SqlConnection conn, String sql) {
        return queryInTx(conn, sql, Tuple.tuple());
    }

    /**
     * Execute a prepared query inside a transaction (returns JsonObject list).
     */
    public static Future<List<JsonObject>> queryListInTx(SqlConnection conn, String sql, Tuple params) {
        return queryInTx(conn, sql, params).map(DatabaseVerticle::toJsonList);
    }

    public static Future<List<JsonObject>> queryListInTx(SqlConnection conn, String sql) {
        return queryListInTx(conn, sql, Tuple.tuple());
    }

    /**
     * Execute a prepared query inside a transaction (returns single JsonObject or null).
     */
    public static Future<JsonObject> queryOneInTx(SqlConnection conn, String sql, Tuple params) {
        return queryInTx(conn, sql, params)
            .map(rows -> {
                List<JsonObject> list = toJsonList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    /**
     * Execute an UPDATE/INSERT/DELETE inside a transaction (returns affected row count).
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
        var migrationFuture = vertx.executeBlocking((java.util.concurrent.Callable<Void>) () -> {
            FlywayMigration.migrateSync(host, port, database, user, password, ssl);
            return null;
        });

        migrationFuture.onSuccess(v -> {
            LOG.info("[DB] Migrations complete");
            p.complete();
        }).onFailure(err -> {
            LOG.warn("[DB] Migration warning (non-fatal): {}", err != null ? err.getMessage() : "unknown");
            p.complete();
        });

        return p.future();
    }

    // ================================================================
    // SQL helpers (for dynamic queries, NOT table creation)
    // ================================================================

    private Future<Void> executeSQL(String sql) {
        Promise<Void> p = Promise.promise();
        pool.query(sql).execute()
            .onSuccess(rows -> { LOG.debug("[DB] SQL OK: {} rows", rows.rowCount()); p.complete(); })
            .onFailure(err -> {
                LOG.warn("[DB] SQL failed (non-fatal): {} Error: {}", sql, err.getMessage());
                LOG.debug("[DB] Full SQL error: ", err);
                p.complete();
            });
        return p.future();
    }
}
