package com.example.entity;

import java.time.LocalDateTime;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;

public class SysConfig {
    private Long configId;
    private String configName;
    private String configKey;
    private String configValue;
    private String configType;
    private String description;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private String remark;

    public SysConfig() {}

    public SysConfig(Long configId, String configKey, String configValue) {
        this.configId = configId;
        this.configKey = configKey;
        this.configValue = configValue;
    }

    // --- Getters & Setters ---
    public Long getConfigId() { return configId; }
    public void setConfigId(Long configId) { this.configId = configId; }

    public String getConfigName() { return configName; }
    public void setConfigName(String configName) { this.configName = configName; }

    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }

    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }

    public String getConfigType() { return configType; }
    public void setConfigType(String configType) { this.configType = configType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    // --- Conversions ---
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (configId != null)    json.put("configId",    configId);
        if (configName != null)   json.put("configName",   configName);
        if (configKey != null)    json.put("configKey",    configKey);
        if (configValue != null)  json.put("configValue",  configValue);
        if (configType != null)   json.put("configType",   configType);
        if (description != null) json.put("description", description);
        if (createBy != null)     json.put("createBy",    createBy);
        if (createTime != null)  json.put("createTime",  createTime.toString());
        if (updateBy != null)     json.put("updateBy",    updateBy);
        if (updateTime != null)  json.put("updateTime",  updateTime.toString());
        if (remark != null)      json.put("remark",       remark);
        return json;
    }

    public static SysConfig fromJson(JsonObject json) {
        if (json == null) return null;
        SysConfig c = new SysConfig();
        c.setConfigId(json.getLong("configId") != null ? json.getLong("configId") : json.getLong("config_id"));
        c.setConfigName(json.getString("configName") != null ? json.getString("configName") : json.getString("config_name"));
        c.setConfigKey(json.getString("configKey") != null ? json.getString("configKey") : json.getString("config_key"));
        c.setConfigValue(json.getString("configValue") != null ? json.getString("configValue") : json.getString("config_value"));
        c.setConfigType(json.getString("configType") != null ? json.getString("configType") : json.getString("config_type"));
        c.setDescription(json.getString("description"));
        c.setCreateBy(json.getString("createBy") != null ? json.getString("createBy") : json.getString("create_by"));
        String ct = json.getString("createTime") != null ? json.getString("createTime") : json.getString("create_time");
        if (ct != null) c.setCreateTime(LocalDateTime.parse(ct));
        c.setUpdateBy(json.getString("updateBy") != null ? json.getString("updateBy") : json.getString("update_by"));
        String ut = json.getString("updateTime") != null ? json.getString("updateTime") : json.getString("update_time");
        if (ut != null) c.setUpdateTime(LocalDateTime.parse(ut));
        c.setRemark(json.getString("remark"));
        return c;
    }

    public static SysConfig fromRow(Row row) {
        if (row == null) return null;
        SysConfig c = new SysConfig();
        c.setConfigId(row.getLong("config_id"));
        c.setConfigName(row.getString("config_name"));
        c.setConfigKey(row.getString("config_key"));
        c.setConfigValue(row.getString("config_value"));
        c.setConfigType(row.getString("config_type"));
        c.setDescription(row.getString("description"));
        c.setCreateBy(row.getString("create_by"));
        c.setCreateTime(row.getLocalDateTime("create_time"));
        c.setUpdateBy(row.getString("update_by"));
        c.setUpdateTime(row.getLocalDateTime("update_time"));
        c.setRemark(row.getString("remark"));
        return c;
    }

    public static SysConfig toConfigOne(JsonObject json) { return fromJson(json); }
    public static java.util.List<SysConfig> toConfigList(java.util.List<JsonObject> list) {
        if (list == null) return java.util.Collections.emptyList();
        return list.stream().map(SysConfig::fromJson).collect(java.util.stream.Collectors.toList());
    }
}
