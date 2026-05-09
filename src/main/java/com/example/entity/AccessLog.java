package com.example.entity;

import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AccessLog — 用户访问日志实体
 *
 * <p>记录所有 /api/* 请求的访问行为，支持按用户、路径、方法、状态码、IP、时间等多维度查询。
 * 保留天数通过 sys_config (key = sys.access-log.retentionDays) 配置。
 */
public class AccessLog {

    private Long id;
    private String traceId;
    private Long userId;
    private String username;
    private String method;
    private String path;
    private String queryString;
    private int statusCode;
    private Integer responseTime;
    private String userIp;
    private String userAgent;
    private String requestId;
    private String errorMessage;
    private JsonObject extra;
    private OffsetDateTime createdAt;

    public AccessLog() {}

    // ================================================================
    // Getters / Setters
    // ================================================================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getQueryString() { return queryString; }
    public void setQueryString(String queryString) { this.queryString = queryString; }

    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }

    public Integer getResponseTime() { return responseTime; }
    public void setResponseTime(Integer responseTime) { this.responseTime = responseTime; }

    public String getUserIp() { return userIp; }
    public void setUserIp(String userIp) { this.userIp = userIp; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public JsonObject getExtra() { return extra; }
    public void setExtra(JsonObject extra) { this.extra = extra; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    // ================================================================
    // JSON serialization
    // ================================================================

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (id != null) json.put("id", id);
        if (traceId != null) json.put("traceId", traceId);
        if (userId != null) json.put("userId", userId);
        if (username != null) json.put("username", username);
        if (method != null) json.put("method", method);
        if (path != null) json.put("path", path);
        if (queryString != null) json.put("queryString", queryString);
        json.put("statusCode", statusCode);
        if (responseTime != null) json.put("responseTime", responseTime);
        if (userIp != null) json.put("userIp", userIp);
        if (userAgent != null) json.put("userAgent", userAgent);
        if (requestId != null) json.put("requestId", requestId);
        if (errorMessage != null) json.put("errorMessage", errorMessage);
        if (extra != null) json.put("extra", extra);
        if (createdAt != null) json.put("createdAt", createdAt.toString());
        return json;
    }

    // ================================================================
    // Static factory methods
    // ================================================================

    /** Row -> AccessLog */
    public static AccessLog fromRow(Row row) {
        AccessLog log = new AccessLog();
        log.id = row.getLong("id");
        log.traceId = row.getString("trace_id");
        log.userId = row.getLong("user_id");
        log.username = row.getString("username");
        log.method = row.getString("method");
        log.path = row.getString("path");
        log.queryString = row.getString("query_string");
        Integer sc = row.getInteger("status_code");
        log.statusCode = sc != null ? sc : 0;
        log.responseTime = row.getInteger("response_time");
        log.userIp = row.getString("user_ip");
        log.userAgent = row.getString("user_agent");
        log.requestId = row.getString("request_id");
        log.errorMessage = row.getString("error_message");
        Object ex = row.getValue("extra");
        log.extra = ex instanceof JsonObject ? (JsonObject) ex : null;
        Object created = row.getValue("created_at");
        if (created instanceof OffsetDateTime) {
            log.createdAt = (OffsetDateTime) created;
        }
        return log;
    }

    /** JsonObject -> AccessLog */
    public static AccessLog fromJson(JsonObject json) {
        AccessLog log = new AccessLog();
        if (json.containsKey("id")) {
            Object v = json.getValue("id");
            if (v instanceof Number) log.id = ((Number) v).longValue();
        }
        log.traceId = json.getString("traceId", null);
        Object uid = json.getValue("userId");
        if (uid instanceof Number) log.userId = ((Number) uid).longValue();
        log.username = json.getString("username", null);
        log.method = json.getString("method", null);
        log.path = json.getString("path", null);
        log.queryString = json.getString("queryString", null);
        log.statusCode = json.getInteger("statusCode", 0);
        log.responseTime = json.getInteger("responseTime", null);
        log.userIp = json.getString("userIp", null);
        log.userAgent = json.getString("userAgent", null);
        log.requestId = json.getString("requestId", null);
        log.errorMessage = json.getString("errorMessage", null);
        log.extra = json.getJsonObject("extra", null);
        return log;
    }

    /** RowSet -> List<AccessLog> */
    public static List<AccessLog> toList(RowSet<Row> rows) {
        List<AccessLog> list = new ArrayList<>();
        for (Row row : rows) list.add(fromRow(row));
        return list;
    }

    /** RowSet -> single AccessLog or null */
    public static AccessLog toOne(RowSet<Row> rows) {
        var it = rows.iterator();
        return it.hasNext() ? fromRow(it.next()) : null;
    }
}
