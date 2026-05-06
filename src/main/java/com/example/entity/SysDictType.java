package com.example.entity;

import java.time.LocalDateTime;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;

public class SysDictType {
    private Long dictId;
    private String dictName;
    private String dictType;
    private String status;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private String remark;

    public SysDictType() {}

    public SysDictType(Long dictId, String dictName, String dictType) {
        this.dictId = dictId;
        this.dictName = dictName;
        this.dictType = dictType;
    }

    public Long getDictId() { return dictId; }
    public void setDictId(Long dictId) { this.dictId = dictId; }

    public String getDictName() { return dictName; }
    public void setDictName(String dictName) { this.dictName = dictName; }

    public String getDictType() { return dictType; }
    public void setDictType(String dictType) { this.dictType = dictType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

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

    /** Row → SysDictType */
    public static SysDictType toSysDictType(Row row) {
        SysDictType d = new SysDictType();
        d.setDictId(row.getLong("dict_id"));
        d.setDictName(row.getString("dict_name"));
        d.setDictType(row.getString("dict_type"));
        d.setStatus(row.getString("status"));
        d.setCreateBy(row.getString("create_by"));
        d.setCreateTime(row.getLocalDateTime("create_time"));
        d.setUpdateBy(row.getString("update_by"));
        d.setUpdateTime(row.getLocalDateTime("update_time"));
        d.setRemark(row.getString("remark"));
        return d;
    }

    /** JsonObject → SysDictType (from API request body) */
    public static SysDictType fromJson(JsonObject json) {
        SysDictType d = new SysDictType();
        d.setDictId(json.getLong("dictId"));
        d.setDictName(json.getString("dictName"));
        d.setDictType(json.getString("dictType"));
        d.setStatus(json.getString("status", "0"));
        d.setCreateBy(json.getString("createBy", "admin"));
        d.setUpdateBy(json.getString("updateBy"));
        d.setRemark(json.getString("remark"));
        return d;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (dictId != null) json.put("dictId", dictId);
        if (dictName != null) json.put("dictName", dictName);
        if (dictType != null) json.put("dictType", dictType);
        if (status != null) json.put("status", status);
        if (createBy != null) json.put("createBy", createBy);
        if (createTime != null) json.put("createTime", createTime.toString());
        if (updateBy != null) json.put("updateBy", updateBy);
        if (updateTime != null) json.put("updateTime", updateTime.toString());
        if (remark != null) json.put("remark", remark);
        return json;
    }
}