package com.example;

import com.example.core.Config;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application Entry Point
 *
 * Config: Spring Boot style YAML files loaded via Vert.x ConfigRetriever.
 * Profile activation:
 *   set APP_ENV=DEV   (Windows)
 *   export APP_ENV=DEV   (Linux/macOS)
 *
 * Files (classpath): application.yml + application-{PROFILE}.yml
 */
public class App {

    private static final Logger LOG = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        LOG.info("============================================================");
        LOG.info(" Vert.x Application - Profile: {}", System.getenv("APP_ENV"));
        LOG.info("============================================================");
        LOG.info("[JAVA] {}", System.getProperty("java.version"));
        LOG.info("[OS] {} {}", System.getProperty("os.name"), System.getProperty("os.version"));

        VertxOptions vertxOptions = new VertxOptions()
            .setWorkerPoolSize(10)
            .setInternalBlockingPoolSize(10)
            .setEventLoopPoolSize(Runtime.getRuntime().availableProcessors() * 2);

        Vertx vertx = Vertx.vertx(vertxOptions);

        // Load YAML config (app.yml + optional profile file)
        Config.load(vertx)
            .onSuccess(config -> {
                LOG.info("Config loaded - httpPort={}, dbHost={}",
                    Config.getHttpPort(config), Config.getDbHost(config));

                vertx.deployVerticle("com.example.MainVerticle",
                    new io.vertx.core.DeploymentOptions().setConfig(config))
                    .onSuccess(id -> LOG.info("MainVerticle deployed: {}", id))
                    .onFailure(err -> {
                        LOG.error("Failed to deploy MainVerticle", err);
                        vertx.close();
                    });
            })
            .onFailure(err -> {
                LOG.error("Failed to load configuration", err);
                LOG.info("Deploying with default config (demo mode)");
                vertx.deployVerticle("com.example.MainVerticle")
                    .onFailure(e -> {
                        LOG.error("Failed to deploy", e);
                        vertx.close();
                    });
            });

        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutdown signal received...");
            vertx.close()
                .onSuccess(v -> LOG.info("Vert.x closed gracefully"))
                .onFailure(e -> LOG.error("Error closing Vert.x", e));
        }));
    }
}
