package com.example.db;

import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Database Verticle - PostgreSQL Connection Manager
 * 
 * Manages the PostgreSQL connection pool and provides:
 * - Connection pool management
 * - Auto table creation
 * - Health checks
 */
public class DatabaseVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseVerticle.class);

    public static final String DB_POOL = "db.pool";
    public static final String CONFIG_DB_HOST = "db.host";
    public static final String CONFIG_DB_PORT = "db.port";
    public static final String CONFIG_DB_DATABASE = "db.database";
    public static final String CONFIG_DB_USER = "db.user";
    public static final String CONFIG_DB_PASSWORD = "db.password";
    public static final String CONFIG_DB_POOL_SIZE = "db.poolSize";

    private PgPool pool;

    @Override
    public void start(Promise<Void> startPromise) {
        JsonObject dbConfig = config().getJsonObject("db", new JsonObject());

        String host = getEnvOrConfig(dbConfig, CONFIG_DB_HOST, "localhost");
        int port = getEnvOrConfig(dbConfig, CONFIG_DB_PORT, 5432);
        String database = getEnvOrConfig(dbConfig, CONFIG_DB_DATABASE, "myapp");
        String user = getEnvOrConfig(dbConfig, CONFIG_DB_USER, "postgres");
        String password = getEnvOrConfig(dbConfig, CONFIG_DB_PASSWORD, "postgres");
        int poolSize = getEnvOrConfig(dbConfig, CONFIG_DB_POOL_SIZE, 10);

        PgConnectOptions connectOptions = new PgConnectOptions()
            .setHost(host)
            .setPort(port)
            .setDatabase(database)
            .setUser(user)
            .setPassword(password)
            .setIdleTimeout(30)
            .setConnectTimeout(10_000)
            // SSL mode (disable for local dev)
            .setSslMode(io.vertx.pgclient.SslMode.DISABLE);

        PoolOptions poolOptions = new PoolOptions()
            .setMaxSize(poolSize)
            .setName("myapp-pool")
            .setEventLoopSize(4)
            .setShared(true);

        pool = PgPool.pool(vertx, connectOptions, poolOptions);

        LOG.info("📦 Connecting to PostgreSQL {}:{}/{}", host, port, database);

        // Initialize tables
        initializeTables()
            .compose(v -> {
                // Publish pool to event bus

                // Store pool in context
                context.put(DB_POOL, pool);

                LOG.info("✅ PostgreSQL connected and initialized");
                startPromise.complete();
                return Future.succeededFuture();
            })
            .onFailure(err -> {
                LOG.error("❌ Failed to initialize database", err);
                // Don't fail startup - allow running without DB for dev
                LOG.warn("⚠️  Running in DEMO MODE (no database)");
                startPromise.complete();
            });
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        if (pool != null) {
            pool.close()
                .onSuccess(v -> {
                    LOG.info("🛑 PostgreSQL connection pool closed");
                    stopPromise.complete();
                })
                .onFailure(stopPromise::fail);
        } else {
            stopPromise.complete();
        }
    }

    /**
     * Get the database pool
     */
    public static PgPool getPool(Vertx vertx) {
        return (PgPool) vertx.getOrCreateContext().get(DB_POOL);
    }

    /**
     * Execute a query
     */
    public static Future<RowSet<Row>> query(Vertx vertx, String sql, Tuple params) {
        PgPool pool = getPool(vertx);
        if (pool == null) {
            return Future.failedFuture("Database not available");
        }
        return pool.preparedQuery(sql).execute(params);
    }

    /**
     * Execute a query without params
     */
    public static Future<RowSet<Row>> query(Vertx vertx, String sql) {
        return query(vertx, sql, Tuple.tuple());
    }

    /**
     * Convert RowSet to List<JsonObject>
     */
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

    /**
     * Convert single Row to JsonObject
     */
    public static JsonObject toJson(Row row) {
        JsonObject obj = new JsonObject();
        for (int i = 0; i < row.size(); i++) {
            obj.put(row.getColumnName(i), convertValue(row.getValue(i)));
        }
        return obj;
    }

    private static Object convertValue(Object value) {
        if (value == null) return null;
        if (value instanceof java.time.LocalDate) {
            return value.toString();
        }
        if (value instanceof java.time.LocalDateTime) {
            return value.toString();
        }
        if (value instanceof java.time.OffsetDateTime) {
            return value.toString();
        }
        return value;
    }

    // ================================================================
    // TABLE INITIALIZATION
    // ================================================================

    private Future<Void> initializeTables() {
        return executeSQL("CREATE TABLE IF NOT EXISTS users (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "name VARCHAR(100) NOT NULL, " +
                "email VARCHAR(255) NOT NULL UNIQUE, " +
                "age INTEGER, " +
                "department VARCHAR(100), " +
                "status VARCHAR(20) DEFAULT 'active', " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")")
            .compose(v -> executeSQL("CREATE TABLE IF NOT EXISTS products (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "name VARCHAR(200) NOT NULL, " +
                "category VARCHAR(100), " +
                "price DECIMAL(10,2) NOT NULL, " +
                "stock INTEGER DEFAULT 0, " +
                "description TEXT, " +
                "status VARCHAR(20) DEFAULT 'active', " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")"))
            .compose(v -> insertSampleData());
    }

    private Future<Void> insertSampleData() {
        return executeSQL("INSERT INTO users (name, email, age, department, status) " +
                "SELECT 'Alice', 'alice@example.com', 28, 'Engineering', 'active' " +
                "WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'alice@example.com')")
            .compose(v -> executeSQL("INSERT INTO users (name, email, age, department, status) " +
                "SELECT 'Bob', 'bob@example.com', 32, 'Product', 'active' " +
                "WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'bob@example.com')"))
            .compose(v -> executeSQL("INSERT INTO users (name, email, age, department, status) " +
                "SELECT 'Charlie', 'charlie@example.com', 25, 'Design', 'inactive' " +
                "WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'charlie@example.com')"))
            .compose(v -> executeSQL("INSERT INTO products (name, category, price, stock, description, status) " +
                "SELECT 'iPhone 15', 'Electronics', 799.99, 100, 'Apple smartphone', 'active' " +
                "WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'iPhone 15')"))
            .compose(v -> executeSQL("INSERT INTO products (name, category, price, stock, description, status) " +
                "SELECT 'MacBook Pro', 'Electronics', 1999.99, 50, 'Apple laptop', 'active' " +
                "WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'MacBook Pro')"))
            .compose(v -> executeSQL("INSERT INTO products (name, category, price, stock, description, status) " +
                "SELECT 'Coffee Maker', 'Home', 49.99, 200, 'Automatic coffee maker', 'active' " +
                "WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'Coffee Maker')"))
            .compose(v -> Future.succeededFuture());
    }

    private Future<Void> executeSQL(String sql) {
        Promise<Void> promise = Promise.promise();
        pool.query(sql)
            .execute()
            .onSuccess(rows -> {
                LOG.debug("✅ SQL executed: {} rows affected", rows.rowCount());
                promise.complete();
            })
            .onFailure(err -> {
                LOG.debug("⚠️  SQL warning: {}", err.getMessage());
                promise.complete(); // Don't fail on warnings
            });
        return promise.future();
    }

    private String getEnvOrConfig(JsonObject config, String key, String defaultValue) {
        String envKey = key.replace(".", "_").toUpperCase();
        String envValue = System.getenv(envKey);
        if (envValue != null) return envValue;
        return config.getString(key, defaultValue);
    }

    private int getEnvOrConfig(JsonObject config, String key, int defaultValue) {
        String envKey = key.replace(".", "_").toUpperCase();
        String envValue = System.getenv(envKey);
        if (envValue != null) {
            try {
                return Integer.parseInt(envValue);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return config.getInteger(key, defaultValue);
    }

}
