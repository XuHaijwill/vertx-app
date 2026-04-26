package com.example;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Application Entry Point
 * 
 * Features:
 * - Config loading (file, env, sys)
 * - Cluster mode support
 * - Graceful shutdown
 */
public class App {

    private static final Logger LOG = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        LOG.info("🚀 Starting Vert.x Application...");
        LOG.info("📦 Java Version: {}", System.getProperty("java.version"));
        LOG.info("🖥️  OS: {} {}", System.getProperty("os.name"), System.getProperty("os.version"));

        // Create Vert.x instance with options
        VertxOptions vertxOptions = new VertxOptions()
            // .setClustered(true)  // Enable for cluster mode
            .setWorkerPoolSize(10)
            .setInternalBlockingPoolSize(10)
            .setEventLoopPoolSize(Runtime.getRuntime().availableProcessors() * 2);

        Vertx vertx = Vertx.vertx(vertxOptions);

        // Load configuration and deploy verticle
        loadConfig(vertx)
            .onSuccess(config -> {
                LOG.info("✅ Configuration loaded");
                LOG.info("   HTTP Port: {}", config.getInteger("http.port", 8888));

                // Deploy MainVerticle with config
                vertx.deployVerticle("com.example.MainVerticle",
                    new io.vertx.core.DeploymentOptions().setConfig(config),
                    ar -> {
                        if (ar.succeeded()) {
                            LOG.info("✅ MainVerticle deployed: {}", ar.result());
                        } else {
                            LOG.error("❌ Failed to deploy MainVerticle", ar.cause());
                            vertx.close();
                        }
                    });
            })
            .onFailure(err -> {
                LOG.error("❌ Failed to load configuration", err);
                LOG.info("📝 Using default configuration");
                
                // Deploy with empty config (will use defaults)
                vertx.deployVerticle("com.example.MainVerticle",
                    ar -> {
                        if (ar.failed()) {
                            LOG.error("❌ Failed to deploy", ar.cause());
                            vertx.close();
                        }
                    });
            });

        // Graceful shutdown handlers
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("🛑 Received shutdown signal...");
            vertx.close(ar -> {
                if (ar.succeeded()) {
                    LOG.info("✅ Vert.x instance closed gracefully");
                } else {
                    LOG.error("❌ Error closing Vert.x instance", ar.cause());
                }
                System.exit(0);
            });
        }));
    }

    /**
     * Load configuration from multiple sources
     * Priority: System properties > Environment variables > File config > Default config
     */
    private static io.vertx.core.Future<JsonObject> loadConfig(Vertx vertx) {
        // Default configuration
        ConfigStoreOptions defaultConfig = new ConfigStoreOptions()
            .setType("json")
            .setConfig(new JsonObject()
                .put("http.port", 8888)
                .put("db.host", System.getenv().getOrDefault("DB_HOST", "localhost"))
                .put("db.port", Integer.parseInt(System.getenv().getOrDefault("DB_PORT", "3306")))
                .put("db.database", System.getenv().getOrDefault("DB_NAME", "mydb"))
                .put("db.user", System.getenv().getOrDefault("DB_USER", "root"))
                .put("db.password", System.getenv().getOrDefault("DB_PASSWORD", ""))
            );

        // File-based configuration (config.json in current dir or classpath)
        ConfigStoreOptions fileConfig = new ConfigStoreOptions()
            .setType("file")
            .setOptional(true)
            .setConfig(new JsonObject()
                .put("path", "config.json")
            );

        // YAML configuration (optional)
        ConfigStoreOptions yamlConfig = new ConfigStoreOptions()
            .setType("yaml")
            .setOptional(true)
            .setConfig(new JsonObject()
                .put("path", "config.yaml")
            );

        // Environment variables
        ConfigStoreOptions envConfig = new ConfigStoreOptions()
            .setType("env")
            .setOptional(true);

        // System properties
        ConfigStoreOptions sysConfig = new ConfigStoreOptions()
            .setType("sys")
            .setOptional(true);

        ConfigRetrieverOptions options = new ConfigRetrieverOptions()
            .addStore(defaultConfig)
            .addStore(fileConfig)
            .addStore(yamlConfig)
            .addStore(envConfig)
            .addStore(sysConfig)
            .setScanPeriod(5000);  // Watch for config changes (dev mode)

        ConfigRetriever retriever = ConfigRetriever.create(vertx, options);

        // Log config changes
        retriever.listen(change -> {
            LOG.info("📄 Configuration updated: {}", change.getNewConfiguration().encodePrettily());
        });

        return retriever.getConfig();
    }
}