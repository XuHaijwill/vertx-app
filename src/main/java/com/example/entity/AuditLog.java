package com.example.entity;

import java.time.LocalDateTime;
import io.vertx.core.json.JsonObject;

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
}
