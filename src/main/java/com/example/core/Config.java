package com.example.core;


import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Spring Boot style YAML configuration loader with profile support.
 *
 * <p>Files live in the <code>config/</code> directory next to the JAR
 * (or next to pom.xml during development). Credentials stay out of the JAR.</p>
 *
 * <p>Profile activation:</p>
 * <ul>
 *   <li>Environment variable: <code>set APP_ENV=DEV</code></li>
 *   <li>System property: <code>-Dapp.profile=DEV</code></li>
 * </ul>
 *
 * <p>Files:</p>
 * <ul>
 *   <li><code>config/application.yml</code> — shared defaults</li>
 *   <li><code>config/application-DEV.yml</code></li>
 *   <li><code>config/application-UAT.yml</code></li>
 *   <li><code>config/application-PROD.yml</code></li>
 * </ul>
 *
 * <p>Priority (lowest → highest): application.yml → application-{PROFILE}.yml</p>
 */
public class Config {

    private static final Logger LOG = LoggerFactory.getLogger(Config.class);

    private static final String CONFIG_DIR = "config";

    // Config key constants
    public static final String KEY_APP_PROFILE  = "app.profile";
    public static final String KEY_APP_NAME       = "app.name";
    public static final String KEY_APP_HTTP_PORT  = "app.http-port";
    public static final String KEY_APP_CONTEXT_PATH = "app.context-path";

    public static final String KEY_DB_HOST      = "db.host";
    public static final String KEY_DB_PORT      = "db.port";
    public static final String KEY_DB_DATABASE  = "db.database";
    public static final String KEY_DB_USER      = "db.user";
    public static final String KEY_DB_PASSWORD  = "db.password";
    public static final String KEY_DB_POOL_SIZE = "db.pool-size";
    public static final String KEY_DB_SSL       = "db.ssl";

    public static final String KEY_AUTH_ENABLED       = "auth.enabled";
    public static final String KEY_AUTH_JWKS_URI      = "auth.jwks-uri";
    public static final String KEY_AUTH_ISSUER         = "auth.issuer";
    public static final String KEY_AUTH_CLIENT_ID      = "auth.client-id";
    public static final String KEY_AUTH_AUDIENCE       = "auth.audience";
    public static final String KEY_AUTH_REALM          = "auth.realm";
    public static final String KEY_AUTH_SERVER_URL     = "auth.auth-server-url";

    public static final String KEY_SCHEDULER_ENABLED         = "scheduler.enabled";
    public static final String KEY_SCHEDULER_POLL_SECS       = "scheduler.poll-seconds";
    public static final String KEY_SCHEDULER_HTTP_TIMEOUT_SECS = "scheduler.http-timeout-seconds";

    // Cache config keys
    public static final String KEY_CACHE_ENABLED      = "cache.enabled";
    public static final String KEY_CACHE_MAX_SIZE    = "cache.max-size";
    public static final String KEY_CACHE_TTL_MINUTES = "cache.ttl-minutes";

    public static final String PROFILE_DEV  = "DEV";
    public static final String PROFILE_UAT  = "UAT";
    public static final String PROFILE_PROD = "PROD";

    private static final Yaml YAML = new Yaml();

    // ================================================================
    // Public API
    // ================================================================

    /**
     * Load and merge YAML config files, return as a Future&lt;JsonObject&gt;.
     *
     * <p>Uses ConfigRetriever only for file-change watching (hot-reload).
     * YAML parsing is done directly with SnakeYAML.</p>
     */
    public static Future<JsonObject> load(Vertx vertx) {
        String profile = resolveProfile();
        String baseFile = CONFIG_DIR + "/application.yml";
        String profileFile = profile.isEmpty() ? null
            : CONFIG_DIR + "/application-" + profile + ".yml";

        LOG.info("📄 Config — profile={}, base={}, overlay={}",
            profile.isEmpty() ? "(default)" : profile,
            baseFile,
            profileFile != null ? profileFile : "none");

        // 1. Parse base YAML → Map → JsonObject
        JsonObject base = readYamlFile(baseFile, LOG);
        if (base == null) {
            LOG.warn("⚠️  {} not found — using empty config", baseFile);
            base = new JsonObject();
        }

        // 2. Merge profile YAML on top (profile wins for duplicate keys)
        JsonObject merged = base.copy();
        if (profileFile != null) {
            JsonObject overlay = readYamlFile(profileFile, LOG);
            if (overlay != null) {
                mergeInto(merged, overlay);
            } else {
                LOG.warn("⚠️  Profile file not found: {}", profileFile);
            }
        }

        // 3. Stamp active profile
        merged.put(KEY_APP_PROFILE, profile);

        LOG.info("✅ Config loaded — httpPort={}, dbHost={}, profile={}",
            getHttpPort(merged), getDbHost(merged), profile);

        // 4. Watch for file changes using ConfigRetriever (low-priority store)
        watchForChanges(vertx, baseFile, profileFile);

        return Future.succeededFuture(merged);
    }

    /**
     * Placeholder for future hot-reload via file watcher.
     * Currently disabled to avoid ConfigRetriever JSON-processor conflicts.
     */
    private static void watchForChanges(Vertx vertx, String... paths) {
        // No-op: hot-reload can be added later with a proper YAML-aware watcher
    }

    // ================================================================
    // YAML file parsing (SnakeYAML)
    // ================================================================

    /**
     * Read a YAML file and convert to JsonObject.
     * Returns null if the file does not exist.
     */
    private static JsonObject readYamlFile(String path, Logger log) {
        File file = new File(path);
        if (!file.isFile()) {
            log.debug("File not found: {}", path);
            return null;
        }
        try (Reader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            Map<String, Object> yaml = YAML.load(reader);
            if (yaml == null) return new JsonObject();
            return toJsonObject(yaml);
        } catch (IOException e) {
            log.error("Failed to read YAML file: {}", path, e);
            return null;
        }
    }

    /**
     * Recursively convert a YAML Map/List/Scalar to JsonObject/JsonArray/primitive.
     */
    @SuppressWarnings("unchecked")
    private static JsonObject toJsonObject(Map<String, Object> map) {
        JsonObject out = new JsonObject();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            out.put(e.getKey(), toJsonValue(e.getValue()));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object toJsonValue(Object value) {
        if (value == null) return null;
        if (value instanceof Map) return toJsonObject((Map<String, Object>) value);
        if (value instanceof List) {
            JsonArray arr = new JsonArray();
            for (Object item : (List<?>) value) arr.add(toJsonValue(item));
            return arr;
        }
        // SnakeYAML parses numbers as Integer/Long/Double — keep as-is
        return value;
    }

    /**
     * Deep-merge overlay INTO base (overlay wins for duplicate scalar keys).
     * Nested objects are merged recursively.
     */
    @SuppressWarnings("unchecked")
    private static void mergeInto(JsonObject base, JsonObject overlay) {
        for (String key : overlay.fieldNames()) {
            Object ov = overlay.getValue(key);
            if (ov instanceof JsonObject) {
                JsonObject baseChild = base.getJsonObject(key);
                if (baseChild == null) {
                    base.put(key, new JsonObject());
                    baseChild = base.getJsonObject(key);
                }
                mergeInto(baseChild, (JsonObject) ov);
            } else {
                base.put(key, ov);
            }
        }
    }

    // ================================================================
    // Profile resolution
    // ================================================================

    private static String resolveProfile() {
        return Optional.ofNullable(System.getenv("APP_ENV"))
            .filter(Config::notBlank)
            .orElseGet(() ->
                Optional.ofNullable(System.getProperty("app.profile"))
                    .filter(Config::notBlank)
                    .orElse("")
            );
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    // ================================================================
    // Accessors
    // ================================================================

    public static int getHttpPort(JsonObject config) {
        // Nested: app.http-port
        JsonObject app = config.getJsonObject("app");
        if (app != null && app.containsKey("http-port")) {
            return app.getInteger("http-port");
        }
        return config.getInteger(KEY_APP_HTTP_PORT, 8888);
    }

    public static String getDbHost(JsonObject config) {
        // Nested: db.host  
        JsonObject db = config.getJsonObject("db");
        if (db != null && db.containsKey("host")) {
            return db.getString("host");
        }
        return config.getString(KEY_DB_HOST, "localhost");
    }

    public static int getDbPort(JsonObject config) {
        JsonObject db = config.getJsonObject("db");
        if (db != null && db.containsKey("port")) {
            return db.getInteger("port");
        }
        return config.getInteger(KEY_DB_PORT, 5432);
    }

    public static String getDbDatabase(JsonObject config) {
        JsonObject db = config.getJsonObject("db");
        if (db != null && db.containsKey("database")) {
            return db.getString("database");
        }
        return config.getString(KEY_DB_DATABASE, "vertx");
    }

    public static String getDbUser(JsonObject config) {
        JsonObject db = config.getJsonObject("db");
        if (db != null && db.containsKey("user")) {
            return db.getString("user");
        }
        return config.getString(KEY_DB_USER, "postgres");
    }

    public static String getDbPassword(JsonObject config) {
        JsonObject db = config.getJsonObject("db");
        if (db != null && db.containsKey("password")) {
            Object pwd = db.getValue("password");
            // SnakeYAML may parse numbers as Integer
            return pwd == null ? "postgres" : pwd.toString();
        }
        return config.getString(KEY_DB_PASSWORD, "postgres");
    }

    public static int getDbPoolSize(JsonObject config) {
        JsonObject db = config.getJsonObject("db");
        if (db != null && db.containsKey("pool-size")) {
            return db.getInteger("pool-size");
        }
        return config.getInteger(KEY_DB_POOL_SIZE, 10);
    }

    public static boolean getDbSsl(JsonObject config) {
        JsonObject db = config.getJsonObject("db");
        if (db != null && db.containsKey("ssl")) {
            return db.getBoolean("ssl");
        }
        return config.getBoolean(KEY_DB_SSL, false);
    }

    public static String getContextPath(JsonObject config) {
        JsonObject app = config.getJsonObject("app");
        if (app != null && app.containsKey("context-path")) {
            String path = app.getString("context-path");
            // Normalize: ensure starts with / and doesn't end with /
            if (path == null) return "";
            path = path.trim();
            if (!path.startsWith("/")) path = "/" + path;
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
            return path;
        }
        return "";
    }

    public static String getProfile(JsonObject config) {
        return config.getString(KEY_APP_PROFILE, "");
    }

    // ================================================================
    // Auth accessors
    // ================================================================

    public static boolean isAuthEnabled(JsonObject config) {
        JsonObject auth = config.getJsonObject("auth");
        if (auth != null && auth.containsKey("enabled")) {
            return auth.getBoolean("enabled");
        }
        return config.getBoolean(KEY_AUTH_ENABLED, false);
    }

    public static String getAuthJwksUri(JsonObject config) {
        JsonObject auth = config.getJsonObject("auth");
        if (auth != null && auth.containsKey("jwks-uri")) {
            return auth.getString("jwks-uri");
        }
        return config.getString(KEY_AUTH_JWKS_URI, "");
    }

    public static String getAuthIssuer(JsonObject config) {
        JsonObject auth = config.getJsonObject("auth");
        if (auth != null && auth.containsKey("issuer")) {
            return auth.getString("issuer");
        }
        return config.getString(KEY_AUTH_ISSUER, "");
    }

    public static String getAuthClientId(JsonObject config) {
        JsonObject auth = config.getJsonObject("auth");
        if (auth != null && auth.containsKey("client-id")) {
            return auth.getString("client-id");
        }
        return config.getString(KEY_AUTH_CLIENT_ID, "");
    }

    public static String getAuthRealm(JsonObject config) {
        JsonObject auth = config.getJsonObject("auth");
        if (auth != null && auth.containsKey("realm")) {
            return auth.getString("realm");
        }
        return config.getString(KEY_AUTH_REALM, "");
    }

    public static String getAuthServerUrl(JsonObject config) {
        JsonObject auth = config.getJsonObject("auth");
        if (auth != null && auth.containsKey("auth-server-url")) {
            return auth.getString("auth-server-url");
        }
        return config.getString(KEY_AUTH_SERVER_URL, "");
    }

    // ================================================================
    // Scheduler accessors
    // ================================================================

    public static boolean isSchedulerEnabled(JsonObject config) {
        JsonObject scheduler = config.getJsonObject("scheduler");
        if (scheduler != null && scheduler.containsKey("enabled")) {
            return scheduler.getBoolean("enabled");
        }
        return config.getBoolean(KEY_SCHEDULER_ENABLED, true);
    }

    public static int getSchedulerPollSeconds(JsonObject config) {
        JsonObject scheduler = config.getJsonObject("scheduler");
        if (scheduler != null && scheduler.containsKey("poll-seconds")) {
            return scheduler.getInteger("poll-seconds");
        }
        return config.getInteger(KEY_SCHEDULER_POLL_SECS, 30);
    }

    public static int getSchedulerHttpTimeoutSeconds(JsonObject config) {
        JsonObject scheduler = config.getJsonObject("scheduler");
        if (scheduler != null && scheduler.containsKey("http-timeout-seconds")) {
            return scheduler.getInteger("http-timeout-seconds");
        }
        return config.getInteger(KEY_SCHEDULER_HTTP_TIMEOUT_SECS, 60);
    }

    // ===============================================================
    // Cache accessors
    // ===============================================================

    public static boolean isCacheEnabled(JsonObject config) {
        JsonObject cache = config.getJsonObject("cache");
        if (cache != null && cache.containsKey("enabled")) {
            return cache.getBoolean("enabled");
        }
        return config.getBoolean(KEY_CACHE_ENABLED, true);
    }

    public static int getCacheMaxSize(JsonObject config) {
        JsonObject cache = config.getJsonObject("cache");
        if (cache != null && cache.containsKey("max-size")) {
            return cache.getInteger("max-size");
        }
        return config.getInteger(KEY_CACHE_MAX_SIZE, 10000);
    }

    public static long getCacheTtlMinutes(JsonObject config) {
        JsonObject cache = config.getJsonObject("cache");
        if (cache != null && cache.containsKey("ttl-minutes")) {
            return cache.getLong("ttl-minutes");
        }
        return config.getLong(KEY_CACHE_TTL_MINUTES, 60L);
    }
}
