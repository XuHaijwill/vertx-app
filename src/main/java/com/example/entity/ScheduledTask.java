package com.example.entity;

import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;

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
}