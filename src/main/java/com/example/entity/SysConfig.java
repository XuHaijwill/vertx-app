package com.example.entity;

import java.time.LocalDateTime;
import io.vertx.core.json.JsonObject;

public class SysConfig {
    private Long configId;
    private String configName;
    private String configKey;
    private String configValue;
    private String configType;
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

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (configId != null) json.put("configId", configId);
        if (configName != null) json.put("configName", configName);
        if (configKey != null) json.put("configKey", configKey);
        if (configValue != null) json.put("configValue", configValue);
        if (configType != null) json.put("configType", configType);
        if (createBy != null) json.put("createBy", createBy);
        if (createTime != null) json.put("createTime", createTime.toString());
        if (updateBy != null) json.put("updateBy", updateBy);
        if (updateTime != null) json.put("updateTime", updateTime.toString());
        if (remark != null) json.put("remark", remark);
        return json;
    }
}