package com.example.entity;

import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ScheduledTask {
    private Long id;
    private String name;
    private String description;
    private String taskType;
    private JsonObject config;
    private String cron;
    private LocalDateTime nextRunTime;
    private LocalDateTime lastRunTime;
    private String lastRunStatus;
    private String lastRunMessage;
    private Long runCount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ScheduledTask() {}

    public ScheduledTask(Long id, String name, String taskType, String status) {
        this.id = id;
        this.name = name;
        this.taskType = taskType;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }

    public JsonObject getConfig() { return config; }
    public void setConfig(JsonObject config) { this.config = config; }

    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }

    public LocalDateTime getNextRunTime() { return nextRunTime; }
    public void setNextRunTime(LocalDateTime nextRunTime) { this.nextRunTime = nextRunTime; }

    public LocalDateTime getLastRunTime() { return lastRunTime; }
    public void setLastRunTime(LocalDateTime lastRunTime) { this.lastRunTime = lastRunTime; }

    public String getLastRunStatus() { return lastRunStatus; }
    public void setLastRunStatus(String lastRunStatus) { this.lastRunStatus = lastRunStatus; }

    public String getLastRunMessage() { return lastRunMessage; }
    public void setLastRunMessage(String lastRunMessage) { this.lastRunMessage = lastRunMessage; }

    public Long getRunCount() { return runCount; }
    public void setRunCount(Long runCount) { this.runCount = runCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (id != null) json.put("id", id);
        if (name != null) json.put("name", name);
        if (description != null) json.put("description", description);
        if (taskType != null) json.put("taskType", taskType);
        if (config != null) json.put("config", config);
        if (cron != null) json.put("cron", cron);
        if (nextRunTime != null) json.put("nextRunTime", nextRunTime.toString());
        if (lastRunTime != null) json.put("lastRunTime", lastRunTime.toString());
        if (lastRunStatus != null) json.put("lastRunStatus", lastRunStatus);
        if (lastRunMessage != null) json.put("lastRunMessage", lastRunMessage);
        if (runCount != null) json.put("runCount", runCount);
        if (status != null) json.put("status", status);
        if (createdAt != null) json.put("createdAt", createdAt.toString());
        if (updatedAt != null) json.put("updatedAt", updatedAt.toString());
        return json;
    }

    // ================================================================
    // Static factory methods
    // ================================================================

    /** Row → ScheduledTask */
    public static ScheduledTask fromRow(Row row) {
        ScheduledTask task = new ScheduledTask();
        task.id = row.getLong("id");
        task.name = row.getString("name");
        task.description = row.getString("description");
        task.taskType = row.getString("task_type");
        Object cfg = row.getValue("config");
        task.config = cfg instanceof JsonObject ? (JsonObject) cfg : null;
        task.cron = row.getString("cron");
        Object nrt = row.getValue("next_run_time");
        task.nextRunTime = nrt instanceof LocalDateTime ? (LocalDateTime) nrt : null;
        Object lrt = row.getValue("last_run_time");
        task.lastRunTime = lrt instanceof LocalDateTime ? (LocalDateTime) lrt : null;
        task.lastRunStatus = row.getString("last_run_status");
        task.lastRunMessage = row.getString("last_run_message");
        task.runCount = row.getLong("run_count");
        task.status = row.getString("status");
        Object created = row.getValue("created_at");
        task.createdAt = created instanceof LocalDateTime ? (LocalDateTime) created : null;
        Object updated = row.getValue("updated_at");
        task.updatedAt = updated instanceof LocalDateTime ? (LocalDateTime) updated : null;
        return task;
    }

    /** JsonObject → ScheduledTask */
    public static ScheduledTask fromJson(JsonObject json) {
        ScheduledTask task = new ScheduledTask();
        if (json.containsKey("id")) {
            Object v = json.getValue("id");
            if (v instanceof Number) task.id = ((Number) v).longValue();
        }
        task.name = json.getString("name", null);
        task.description = json.getString("description", null);
        task.taskType = json.getString("taskType", null);
        task.config = json.getJsonObject("config", null);
        task.cron = json.getString("cron", null);
        String nrt = json.getString("nextRunTime", null);
        if (nrt != null && !nrt.isBlank()) {
            try { task.nextRunTime = LocalDateTime.parse(nrt); } catch (Exception ignored) { }
        }
        String lrt = json.getString("lastRunTime", null);
        if (lrt != null && !lrt.isBlank()) {
            try { task.lastRunTime = LocalDateTime.parse(lrt); } catch (Exception ignored) { }
        }
        task.lastRunStatus = json.getString("lastRunStatus", null);
        task.lastRunMessage = json.getString("lastRunMessage", null);
        Object rc = json.getValue("runCount");
        if (rc instanceof Number) task.runCount = ((Number) rc).longValue();
        task.status = json.getString("status", null);
        return task;
    }

    /** RowSet → List<ScheduledTask> */
    public static List<ScheduledTask> toList(RowSet<Row> rows) {
        List<ScheduledTask> list = new ArrayList<>();
        for (Row row : rows) list.add(fromRow(row));
        return list;
    }

    /** RowSet → single ScheduledTask or null */
    public static ScheduledTask toOne(RowSet<Row> rows) {
        List<ScheduledTask> list = toList(rows);
        return list.isEmpty() ? null : list.get(0);
    }
}