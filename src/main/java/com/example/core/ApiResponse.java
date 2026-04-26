package com.example.core;

import io.vertx.core.json.JsonObject;

/**
 * Unified API Response Wrapper
 * 统一响应格式
 */
public class ApiResponse {

    public static final String SUCCESS = "success";
    public static final String ERROR = "error";
    public static final String FAIL = "fail";

    private String code;      // 状态码: success/error/fail
    private String message;   // 消息
    private Object data;      // 数据
    private long timestamp;    // 时间戳
    private long duration;    // 请求耗时(ms)

    public ApiResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    // ========== Static Factory Methods ==========

    /**
     * 成功响应
     */
    public static ApiResponse success() {
        return new ApiResponse()
            .setCode(SUCCESS)
            .setMessage("操作成功");
    }

    /**
     * 成功响应 + 数据
     */
    public static ApiResponse success(Object data) {
        return new ApiResponse()
            .setCode(SUCCESS)
            .setMessage("操作成功")
            .setData(data);
    }

    /**
     * 成功响应 + 自定义消息
     */
    public static ApiResponse success(String message, Object data) {
        return new ApiResponse()
            .setCode(SUCCESS)
            .setMessage(message)
            .setData(data);
    }

    /**
     * 错误响应
     */
    public static ApiResponse error(String message) {
        return new ApiResponse()
            .setCode(ERROR)
            .setMessage(message);
    }

    /**
     * 错误响应 + 自定义码
     */
    public static ApiResponse error(String code, String message) {
        return new ApiResponse()
            .setCode(code)
            .setMessage(message);
    }

    /**
     * 失败响应
     */
    public static ApiResponse fail(String message) {
        return new ApiResponse()
            .setCode(FAIL)
            .setMessage(message);
    }

    /**
     * 分页响应
     */
    public static ApiResponse page(Object list, long total, int page, int size) {
        JsonObject pageData = new JsonObject()
            .put("list", list)
            .put("total", total)
            .put("page", page)
            .put("size", size)
            .put("pages", (total + size - 1) / size);

        return new ApiResponse()
            .setCode(SUCCESS)
            .setMessage("查询成功")
            .setData(pageData);
    }

    // ========== Convert to JSON ==========

    public JsonObject toJson() {
        JsonObject json = new JsonObject()
            .put("code", code)
            .put("message", message)
            .put("timestamp", timestamp);

        if (data != null) {
            json.put("data", data);
        }
        if (duration > 0) {
            json.put("duration", duration);
        }

        return json;
    }

    // ========== Getters & Setters ==========

    public String getCode() { return code; }
    public ApiResponse setCode(String code) {
        this.code = code;
        return this;
    }

    public String getMessage() { return message; }
    public ApiResponse setMessage(String message) {
        this.message = message;
        return this;
    }

    public Object getData() { return data; }
    public ApiResponse setData(Object data) {
        this.data = data;
        return this;
    }

    public long getTimestamp() { return timestamp; }
    public ApiResponse setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public long getDuration() { return duration; }
    public ApiResponse setDuration(long duration) {
        this.duration = duration;
        return this;
    }
}