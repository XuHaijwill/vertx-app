package com.example.core;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;

/**
 * Unified API Response Wrapper
 */
public class ApiResponse {

    public static final String SUCCESS = "success";
    public static final String ERROR = "error";
    public static final String FAIL = "fail";

    private String code;
    private String message;
    private Object data;
    private long timestamp;
    private long duration;
    private int _httpStatus = 200;

    // Customizable key mapping (defaults) — thread-safe via ConcurrentHashMap
    private static final java.util.concurrent.ConcurrentHashMap<String, String> keys = new java.util.concurrent.ConcurrentHashMap<>();
    static {
        keys.put("code", "code");
        keys.put("message", "message");
        keys.put("data", "data");
        keys.put("timestamp", "timestamp");
        keys.put("duration", "duration");
    }

    // Extra top-level keys for this response (e.g. permission, count, etc.)
    private final io.vertx.core.json.JsonObject extra = new io.vertx.core.json.JsonObject();

    public ApiResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    // ========== Static Factory Methods ==========

    /** Success response */
    public static ApiResponse success() {
        return new ApiResponse().setCode(SUCCESS).setMessage("Success");
    }

    /** Success response with data */
    public static ApiResponse success(Object data) {
        return new ApiResponse().setCode(SUCCESS).setMessage("Success").setData(data);
    }

    /** Success response with custom message */
    public static ApiResponse success(String message, Object data) {
        return new ApiResponse().setCode(SUCCESS).setMessage(message).setData(data);
    }

    /** Error response */
    public static ApiResponse error(String message) {
        return new ApiResponse().setCode(ERROR).setMessage(message);
    }

    /** Error response with code */
    public static ApiResponse error(String code, String message) {
        return new ApiResponse().setCode(code).setMessage(message);
    }

    /** Fail response */
    public static ApiResponse fail(String message) {
        return new ApiResponse().setCode(FAIL).setMessage(message);
    }

    /** Paginated response */
    public static ApiResponse page(Object list, long total, int page, int size) {
        JsonObject pageData = new JsonObject()
            .put("list", list)
            .put("total", total)
            .put("page", page)
            .put("size", size)
            .put("pages", (total + size - 1) / size);
        return new ApiResponse().setCode(SUCCESS).setMessage("Query successful").setData(pageData);
    }

    /**
     * Add a custom top-level field to this response.
     * Allows per-response extra fields like permission, count, tags, etc.
     *
     * <p>Example — add permission to auth/me response:
     * <pre>
     * ctx.json(new ApiResponse()
     *     .success(userData)
     *     .putExtra("permission", roles)
     *     .toJson());
     * </pre>
     * Produces: { code:"success", data:{...}, permission:[...], timestamp:... }
     *
     * @param key   field name
     * @param value field value (any JSON-compatible type)
     * @return this (fluent)
     */
    public ApiResponse putExtra(String key, Object value) {
        if (key != null && !key.isEmpty()) {
            extra.put(key, value);
        }
        return this;
    }

    /**
     * Add multiple custom top-level fields at once.
     *
     * <p>Example:
     * <pre>
     * new ApiResponse().success(data).putExtras("permission", roles, "count", 42)
     * </pre>
     *
     * @param kvPairs key, value, key, value... (must be even count)
     * @return this (fluent)
     */
    public ApiResponse putExtras(Object... kvPairs) {
        if (kvPairs != null) {
            for (int i = 0; i < kvPairs.length - 1; i += 2) {
                if (kvPairs[i] instanceof String k) {
                    putExtra(k, kvPairs[i + 1]);
                }
            }
        }
        return this;
    }

    /**
     * Mark this response with a specific HTTP status code.
     * The actual status is set by ctx.response().setStatusCode() in handlers;
     * this field is used when a handler returns the ApiResponse's toJson()
     * directly and still needs the correct status recorded.
     */
    public ApiResponse withStatus(int httpStatus) {
        this._httpStatus = httpStatus;
        return this;
    }

    public int getHttpStatus() { return _httpStatus; }

    // ========== Convert to JSON ==========

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        // Use configured keys (fall back to defaults)
        String kCode = keys.getOrDefault("code", "code");
        String kMsg = keys.getOrDefault("message", "message");
        String kData = keys.getOrDefault("data", "data");
        String kTs = keys.getOrDefault("timestamp", "timestamp");
        String kDur = keys.getOrDefault("duration", "duration");

        json.put(kCode, code).put(kMsg, message).put(kTs, timestamp);
        if (data != null) json.put(kData, serializeData(data));
        if (duration > 0) json.put(kDur, duration);
        // Merge all extra custom fields
        for (String k : extra.fieldNames()) {
            json.put(k, extra.getValue(k));
        }
        return json;
    }

    /**
     * Configure response key mapping from application config. Expecting config
     * structure: response: { keys: { code: "status", message: "msg", data: "payload" } }
     */
    /**
     * Serialize data to a JSON-compatible value.
     * Handles entity types with a toJson() method (via reflection).
     * Vert.x JsonObject.put(Object) already handles List/Set of primitives,
     * but NOT List<Entity> — we fix that here.
     */
    private Object serializeData(Object data) {
        if (data instanceof List list) {
            JsonArray arr = new JsonArray();
            for (Object item : list) {
                if (item instanceof JsonObject || item instanceof JsonArray) arr.add(item);
                else if (item != null) {
                    try {
                        java.lang.reflect.Method m = item.getClass().getMethod("toJson");
                        Object result = m.invoke(item);
                        if (result instanceof JsonObject) arr.add((JsonObject) result);
                        else arr.add(item);
                    } catch (Exception ignored) {
                        arr.add(item);
                    }
                } else {
                    arr.addNull();
                }
            }
            return arr;
        }
        return serializeNonList(data);
    }

    /**
     * Serialize a single non-list value to JSON.
     * Tries toJson() via reflection for entity types, otherwise returns as-is.
     */
    private Object serializeNonList(Object data) {
        if (data == null) return null;
        if (data instanceof JsonObject || data instanceof JsonArray) return data;
        try {
            java.lang.reflect.Method m = data.getClass().getMethod("toJson");
            Object result = m.invoke(data);
            if (result instanceof JsonObject) return (JsonObject) result;
        } catch (Exception ignored) { }
        return data;
    }

    public static void configure(io.vertx.core.json.JsonObject config) {
        if (config == null) return;
        io.vertx.core.json.JsonObject resp = config.getJsonObject("response");
        if (resp == null) return;
        io.vertx.core.json.JsonObject k = resp.getJsonObject("keys");
        if (k == null) return;
        // Merge provided keys into defaults (thread-safe)
        for (String field : k.fieldNames()) {
            String val = k.getString(field);
            if (val != null && !val.isEmpty()) keys.put(field, val);
        }
    }

    // ========== Getters & Setters ==========

    public String getCode() { return code; }
    public ApiResponse setCode(String code) { this.code = code; return this; }

    public String getMessage() { return message; }
    public ApiResponse setMessage(String message) { this.message = message; return this; }

    public Object getData() { return data; }
    public ApiResponse setData(Object data) { this.data = data; return this; }

    public long getTimestamp() { return timestamp; }
    public ApiResponse setTimestamp(long ts) { this.timestamp = ts; return this; }

    public long getDuration() { return duration; }
    public ApiResponse setDuration(long d) { this.duration = d; return this; }
}