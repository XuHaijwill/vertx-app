package com.example.db;

import com.example.core.Config;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private PgPool pool;

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

        LOG.info("🔧 DB config — host={}:{}/{} user={} poolSize={} ssl={}",
                 host, port, database, user, poolSize, ssl);

        // Demo mode: host is the default (localhost) AND password is default → no real DB
        boolean demoMode = (host.equals("localhost") && password.equals("postgres"));

        if (demoMode) {
            LOG.warn("⚠️  No DB configured — running DEMO MODE (no database)");
            startPromise.complete();
            return;
        }

        PgConnectOptions connectOptions = new PgConnectOptions()
            .setHost(host)
            .setPort(port)
            .setDatabase(database)
            .setUser(user)
            .setPassword(password)
            .setIdleTimeout(30)
            .setConnectTimeout(10_000)
            .setSslMode(ssl ? io.vertx.pgclient.SslMode.REQUIRE : io.vertx.pgclient.SslMode.DISABLE);

        PoolOptions poolOpts = new PoolOptions()
            .setMaxSize(poolSize)
            .setName("vertx-app-pool")
            .setEventLoopSize(4)
            .setShared(true);

        pool = PgPool.pool(vertx, connectOptions, poolOpts);

        LOG.info("📦 Connecting to PostgreSQL {}:{}/{}", host, port, database);

        initializeTables()
            .compose(v -> {
                context.put(DB_POOL, pool);
                // Publish connection URI to sharedData so other verticles can get the shared pool.
                // PgPoolImpl can't be stored in sharedData, so we store the URI string instead.
                String connUri = String.format("postgresql://%s:%s@%s:%d/%s", user, password, host, port, database);
                vertx.sharedData().getLocalMap("db").put("conn-uri", connUri);
                LOG.info("✅ PostgreSQL connected");
                startPromise.complete();
                return Future.succeededFuture();
            })
            .onFailure(err -> {
                LOG.error("❌ Failed to initialize database", err);
                LOG.warn("⚠️  Running in DEMO MODE");
                startPromise.complete();
            });
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        if (pool != null) {
            pool.close()
                .onSuccess(v -> { LOG.info("🛑 DB pool closed"); stopPromise.complete(); })
                .onFailure(stopPromise::fail);
        } else {
            stopPromise.complete();
        }
    }

    // ================================================================
    // Public helpers
    // ================================================================

    public static PgPool getPool(Vertx vertx) {
        if (vertx == null) return null;
        // Try context first (same-verticle access)
        PgPool p = (PgPool) vertx.getOrCreateContext().get(DB_POOL);
        if (p != null) return p;
        // Try sharedData: parse stored URI to rebuild shared pool.
        // Vert.x returns the SAME pool instance when name + connectOptions match.
        var map = vertx.sharedData().getLocalMap("db");
        String connUri = map != null ? (String) map.get("conn-uri") : null;
        if (connUri != null) {
            try {
                // Rebuild connect options from URI and create shared pool
                PgConnectOptions opts = PgConnectOptions.fromUri(connUri);
                PoolOptions poolOpts = new PoolOptions()
                    .setMaxSize(10)
                    .setName("vertx-app-pool")
                    .setShared(true);
                return PgPool.pool(vertx, opts, poolOpts);
            } catch (Exception e) {
                LOG.warn("⚠️  Failed to rebuild shared pool from URI", e);
            }
        }
        return null;
    }

    public static Future<RowSet<Row>> query(Vertx vertx, String sql, Tuple params) {
        PgPool pool = getPool(vertx);
        if (pool == null) {
            return Future.failedFuture("Database not available (demo mode)");
        }
        return pool.preparedQuery(sql).execute(params);
    }

    public static Future<RowSet<Row>> query(Vertx vertx, String sql) {
        return query(vertx, sql, Tuple.tuple());
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
    // Table initialization
    // ================================================================

    private Future<Void> initializeTables() {
        return executeSQL(
                "CREATE TABLE IF NOT EXISTS users (" +
                "id BIGSERIAL PRIMARY KEY," +
                "name VARCHAR(100) NOT NULL," +
                "email VARCHAR(255) NOT NULL UNIQUE," +
                "age INTEGER," +
                "department VARCHAR(100)," +
                "status VARCHAR(20) DEFAULT 'active'," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)")
            .compose(v -> executeSQL(
                "CREATE TABLE IF NOT EXISTS products (" +
                "id BIGSERIAL PRIMARY KEY," +
                "name VARCHAR(200) NOT NULL," +
                "category VARCHAR(100)," +
                "price DECIMAL(10,2) NOT NULL," +
                "stock INTEGER DEFAULT 0," +
                "description TEXT," +
                "status VARCHAR(20) DEFAULT 'active'," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"))
            .compose(v -> insertSampleData());
    }

    private Future<Void> insertSampleData() {
        return
            executeSQL("INSERT INTO users (name,email,age,department,status) " +
                "SELECT 'Alice','alice@example.com',28,'Engineering','active' " +
                "WHERE NOT EXISTS (SELECT 1 FROM users WHERE email='alice@example.com')")
            .compose(v -> executeSQL("INSERT INTO users (name,email,age,department,status) " +
                "SELECT 'Bob','bob@example.com',32,'Product','active' " +
                "WHERE NOT EXISTS (SELECT 1 FROM users WHERE email='bob@example.com')"))
            .compose(v -> executeSQL("INSERT INTO users (name,email,age,department,status) " +
                "SELECT 'Charlie','charlie@example.com',25,'Design','inactive' " +
                "WHERE NOT EXISTS (SELECT 1 FROM users WHERE email='charlie@example.com')"))
            .compose(v -> executeSQL("INSERT INTO products (name,category,price,stock,description,status) " +
                "SELECT 'iPhone 15','Electronics',799.99,100,'Apple smartphone','active' " +
                "WHERE NOT EXISTS (SELECT 1 FROM products WHERE name='iPhone 15')"))
            .compose(v -> executeSQL("INSERT INTO products (name,category,price,stock,description,status) " +
                "SELECT 'MacBook Pro','Electronics',1999.99,50,'Apple laptop','active' " +
                "WHERE NOT EXISTS (SELECT 1 FROM products WHERE name='MacBook Pro')"))
            .compose(v -> executeSQL("INSERT INTO products (name,category,price,stock,description,status) " +
                "SELECT 'Coffee Maker','Home',49.99,200,'Automatic coffee maker','active' " +
                "WHERE NOT EXISTS (SELECT 1 FROM products WHERE name='Coffee Maker')"))
            .compose(v -> Future.succeededFuture());
    }

    private Future<Void> executeSQL(String sql) {
        Promise<Void> p = Promise.promise();
        pool.query(sql).execute()
            .onSuccess(rows -> { LOG.debug("SQL OK: {} rows", rows.rowCount()); p.complete(); })
            .onFailure(err -> {
                // Log full error so failures to run DDL are visible during startup
                LOG.warn("SQL execution failed (non-fatal). SQL: {} Error: {}", sql, err.getMessage());
                LOG.debug("Full SQL error: ", err);
                p.complete();
            });
        return p.future();
    }
}
