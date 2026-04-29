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
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import io.vertx.sqlclient.Transaction;

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

        // Demo mode: host is the default (localhost) AND password is default → no real DB
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

        // Vert.x 5: PgPool is removed, use PgBuilder.builder().with().connectingTo().using().build()
        pool = PgBuilder.pool()
            .with(poolOpts)
            .connectingTo(connectOptions)
            .using(vertx)
            .build();

        LOG.info("[DB] Connecting to PostgreSQL {}:{}/{}", host, port, database);

        // Run Flyway migrations BEFORE serving traffic
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
                // Vert.x 5: use PgBuilder instead of PgPool.pool()
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
     * Execute a series of database operations within a transaction.
     *
     * <p>All operations succeed  → transaction is committed automatically.
     * Any operation fails       → transaction is rolled back automatically.
     * The transaction is always closed at the end.
     *
     * <p>Usage example:
     * <pre>
     * return DatabaseVerticle.withTransaction(vertx, tx -&gt;
     *     tx.preparedQuery("INSERT INTO orders ...").execute(params1)
     *       .compose(r1 -&gt; tx.preparedQuery("INSERT INTO items ...").execute(params2))
     *       .map(r2 -&gt; r2.iterator().next().getLong("id"))
     * );
     * </pre>
     *
     * @param vertx  Vert.x instance
     * @param block  Lambda receiving the Transaction; return a Future representing the work.
     *               The block must NOT call commit() or rollback() itself — that is
     *               handled automatically based on the returned Future's outcome.
     * @param <T>    Result type of the transaction
     * @return Future that completes with the block's result or fails if any step fails
     */
    public static <T> Future<T> withTransaction(Vertx vertx,
                                                  Function<Transaction, Future<T>> block) {
        Pool pool = getPool(vertx);
        if (pool == null) {
            return Future.failedFuture("Database not available (demo mode)");
        }

        return pool.getConnection()
            .compose(conn ->
                conn.begin()
                    .compose(tx ->
                        block.apply(tx)
                            .compose(
                                result -> tx.commit()
                                    .onComplete(ar -> conn.close())
                                    .map(result),
                                err -> tx.rollback()
                                    .onComplete(ar -> conn.close())
                                    .compose(v -> Future.<T>failedFuture(err))
                            )
                    )
                    .onFailure(err -> conn.close())
            )
            .onFailure(err ->
                LOG.error("[DB-TX] Transaction failed and was rolled back: {}", err.getMessage()));
    }

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
        // Run Flyway migrations on a worker thread to avoid blocking the event-loop
        Promise<Void> p = Promise.promise();
        // Use the Callable-based executeBlocking overload that returns a Future
        var migrationFuture = vertx.executeBlocking((java.util.concurrent.Callable<Void>) () -> {
            // This runs on a worker thread; call the synchronous migration helper which may throw
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
