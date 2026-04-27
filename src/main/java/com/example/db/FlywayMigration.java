package com.example.db;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flyway Database Migration Runner.
 *
 * <p>Migration files live in the classpath under
 * <code>db/migration/</code> and are packaged inside the JAR.</p>
 *
 * <p>Naming convention:</p>
 * <ul>
 *   <li><code>V1__init_schema.sql</code> — create tables, indexes</li>
 *   <li><code>V2__seed_data.sql</code>   — insert sample data</li>
 * </ul>
 *
 * <p>Flyway detects applied migrations from its <code>flyway_schema_history</code>
 * table and skips already-executed ones.</p>
 *
 * <p>This class is used by {@link DatabaseVerticle} before the connection pool
 * is built. It is also accessible via <code>mvn flyway:migrate</code>.</p>
 */
public class FlywayMigration {

    private static final Logger LOG = LoggerFactory.getLogger(FlywayMigration.class);

    /**
     * Run all pending Flyway migrations using the given DB connection parameters.
     *
     * @param host     PostgreSQL host
     * @param port     PostgreSQL port
     * @param database database name
     * @param user     database user
     * @param password database password
     * @param ssl      true → sslmode=require, false → sslmode=disable
     * @return Future completed when migrations finish (or skipped if none pending)
     */
    public static Future<Void> migrate(
            String host, int port, String database,
            String user, String password, boolean ssl) {

        Promise<Void> p = Promise.promise();

        String jdbcUrl = String.format(
            "jdbc:postgresql://%s:%d/%s?sslmode=%s",
            host, port, database,
            ssl ? "require" : "disable"
        );

        LOG.info("[Flyway] Connecting to {} to check migrations...", jdbcUrl);

        try {
            Flyway flyway = Flyway.configure()
                // Load migrations from classpath: db/migration/
                .dataSource(jdbcUrl, user, password)
                // Also scan ./config directory (for dev, outside JAR)
                .locations(
                    "classpath:db/migration",
                    "filesystem:./config"
                )
                .sqlMigrationPrefix("V")
                .sqlMigrationSeparator("__")
                .sqlMigrationSuffixes(".sql")
                // If DB is fresh (no flyway_schema_history), create baseline
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .outOfOrder(false)
                .validateOnMigrate(true)
                .cleanDisabled(true)   // NEVER run clean in production
                .load();

            // Run migrations
            var result = flyway.migrate();

            if (result.migrationsExecuted > 0) {
                LOG.info("[Flyway] ✅ Applied {} migration(s) (db={})",
                    result.migrationsExecuted, database);
            } else {
                LOG.info("[Flyway] No pending migrations (database is up-to-date)");
            }

            p.complete();
        } catch (Exception e) {
            LOG.error("[Flyway] ❌ Migration failed", e);
            p.fail(e);
        }

        return p.future();
    }

    /**
     * Synchronous version of migrate() that runs Flyway.migrate() on the current thread.
     * This is useful when the caller runs migrations from a worker thread or Callable.
     *
     * @throws Exception when migration fails
     */
    public static void migrateSync(
            String host, int port, String database,
            String user, String password, boolean ssl) throws Exception {

        String jdbcUrl = String.format(
            "jdbc:postgresql://%s:%d/%s?sslmode=%s",
            host, port, database,
            ssl ? "require" : "disable"
        );

        LOG.info("[Flyway] Connecting to {} to check migrations...", jdbcUrl);

        Flyway flyway = Flyway.configure()
            // Load migrations from classpath: db/migration/
            .dataSource(jdbcUrl, user, password)
            // Also scan ./config directory (for dev, outside JAR)
            .locations(
                "classpath:db/migration",
                "filesystem:./config"
            )
            .sqlMigrationPrefix("V")
            .sqlMigrationSeparator("__")
            .sqlMigrationSuffixes(".sql")
            // If DB is fresh (no flyway_schema_history), create baseline
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .outOfOrder(false)
            .validateOnMigrate(true)
            .cleanDisabled(true)   // NEVER run clean in production
            .load();

        var result = flyway.migrate();

        if (result.migrationsExecuted > 0) {
            LOG.info("[Flyway] ✅ Applied {} migration(s) (db={})",
                result.migrationsExecuted, database);
        } else {
            LOG.info("[Flyway] No pending migrations (database is up-to-date)");
        }
    }

    /**
     * Convenience overload using a merged config JsonObject.
     */
    public static Future<Void> migrate(JsonObject config) {
        return migrate(
            config.getString("host", "localhost"),
            config.getInteger("port", 5432),
            config.getString("database", "vertx"),
            config.getString("user", "postgres"),
            config.getString("password", "postgres"),
            config.getBoolean("ssl", false)
        );
    }
}
