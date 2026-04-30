package com.example.core;

import io.vertx.core.json.JsonObject;

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

    // Customizable key mapping (defaults)
    private static io.vertx.core.json.JsonObject keys = new io.vertx.core.json.JsonObject()
        .put("code", "code")
        .put("message", "message")
        .put("data", "data")
        .put("timestamp", "timestamp")
        .put("duration", "duration");

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

    // ========== Convert to JSON ==========

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        // Use configured keys (fall back to defaults)
        String kCode = keys.getString("code", "code");
        String kMsg = keys.getString("message", "message");
        String kData = keys.getString("data", "data");
        String kTs = keys.getString("timestamp", "timestamp");
        String kDur = keys.getString("duration", "duration");

        json.put(kCode, code).put(kMsg, message).put(kTs, timestamp);
        if (data != null) json.put(kData, data);
        if (duration > 0) json.put(kDur, duration);
        return json;
    }

    /**
     * Configure response key mapping from application config. Expecting config
     * structure: response: { keys: { code: "status", message: "msg", data: "payload" } }
     */
    public static void configure(io.vertx.core.json.JsonObject config) {
        if (config == null) return;
        io.vertx.core.json.JsonObject resp = config.getJsonObject("response");
        if (resp == null) return;
        io.vertx.core.json.JsonObject k = resp.getJsonObject("keys");
        if (k == null) return;
        // Merge provided keys into defaults
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