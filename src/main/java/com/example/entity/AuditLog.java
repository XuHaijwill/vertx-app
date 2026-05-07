package com.example.entity;

import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AuditLog {
    private Long id;
    private String entityType;
    private String entityId;
    private Long userId;
    private String username;
    private String action;
    private JsonObject oldValues;
    private JsonObject newValues;
    private String status;
    private String errorMessage;
    private String traceId;
    private String clientId;
    private LocalDateTime createdAt;

    public AuditLog() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public JsonObject getOldValues() { return oldValues; }
    public void setOldValues(JsonObject oldValues) { this.oldValues = oldValues; }

    public JsonObject getNewValues() { return newValues; }
    public void setNewValues(JsonObject newValues) { this.newValues = newValues; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (id != null) json.put("id", id);
        if (entityType != null) json.put("entityType", entityType);
        if (entityId != null) json.put("entityId", entityId);
        if (userId != null) json.put("userId", userId);
        if (username != null) json.put("username", username);
        if (action != null) json.put("action", action);
        if (oldValues != null) json.put("oldValues", oldValues);
        if (newValues != null) json.put("newValues", newValues);
        if (status != null) json.put("status", status);
        if (errorMessage != null) json.put("errorMessage", errorMessage);
        if (traceId != null) json.put("traceId", traceId);
        if (clientId != null) json.put("clientId", clientId);
        if (createdAt != null) json.put("createdAt", createdAt.toString());
        return json;
    }

    // ================================================================
    // Static factory methods
    // ================================================================

    /** Row → AuditLog */
    public static AuditLog fromRow(Row row) {
        AuditLog log = new AuditLog();
        log.id = row.getLong("id");
        log.entityType = row.getString("entity_type");
        log.entityId = row.getString("entity_id");
        log.userId = row.getLong("user_id");
        log.username = row.getString("username");
        log.action = row.getString("action");
        Object oldV = row.getValue("old_value");
        log.oldValues = oldV instanceof JsonObject ? (JsonObject) oldV : null;
        Object newV = row.getValue("new_value");
        log.newValues = newV instanceof JsonObject ? (JsonObject) newV : null;
        log.status = row.getString("status");
        log.errorMessage = row.getString("error_message");
        log.traceId = row.getString("trace_id");
        log.clientId = row.getString("client_id");
        Object created = row.getValue("created_at");
        if (created instanceof LocalDateTime) {
            log.createdAt = (LocalDateTime) created;
        }
        return log;
    }

    /** JsonObject → AuditLog */
    public static AuditLog fromJson(JsonObject json) {
        AuditLog log = new AuditLog();
        if (json.containsKey("id")) {
            Object v = json.getValue("id");
            if (v instanceof Number) log.id = ((Number) v).longValue();
        }
        log.entityType = json.getString("entityType", null);
        log.entityId = json.getString("entityId", null);
        Object uid = json.getValue("userId");
        if (uid instanceof Number) log.userId = ((Number) uid).longValue();
        log.username = json.getString("username", null);
        log.action = json.getString("action", null);
        log.oldValues = json.getJsonObject("oldValues", null);
        log.newValues = json.getJsonObject("newValues", null);
        log.status = json.getString("status", null);
        log.errorMessage = json.getString("errorMessage", null);
        log.traceId = json.getString("traceId", null);
        log.clientId = json.getString("clientId", null);
        String createdStr = json.getString("createdAt", null);
        if (createdStr != null && !createdStr.isBlank()) {
            try { log.createdAt = LocalDateTime.parse(createdStr); } catch (Exception ignored) { }
        }
        return log;
    }

    /** RowSet → List<AuditLog> */
    public static List<AuditLog> toList(RowSet<Row> rows) {
        List<AuditLog> list = new ArrayList<>();
        for (Row row : rows) list.add(fromRow(row));
        return list;
    }

    /** RowSet → single AuditLog or null */
    public static AuditLog toOne(RowSet<Row> rows) {
        List<AuditLog> list = toList(rows);
        return list.isEmpty() ? null : list.get(0);
    }
}