package com.example.entity;

import java.time.LocalDateTime;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;

public class SysDictData {
    private Long dictCode;
    private Integer dictSort;
    private String dictLabel;
    private String dictValue;
    private String dictType;
    private String cssClass;
    private String listClass;
    private String isDefault;
    private String status;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private String remark;

    public SysDictData() {}

    public SysDictData(Long dictCode, String dictLabel, String dictValue, String dictType) {
        this.dictCode = dictCode;
        this.dictLabel = dictLabel;
        this.dictValue = dictValue;
        this.dictType = dictType;
    }

    public Long getDictCode() { return dictCode; }
    public void setDictCode(Long dictCode) { this.dictCode = dictCode; }

    public Integer getDictSort() { return dictSort; }
    public void setDictSort(Integer dictSort) { this.dictSort = dictSort; }

    public String getDictLabel() { return dictLabel; }
    public void setDictLabel(String dictLabel) { this.dictLabel = dictLabel; }

    public String getDictValue() { return dictValue; }
    public void setDictValue(String dictValue) { this.dictValue = dictValue; }

    public String getDictType() { return dictType; }
    public void setDictType(String dictType) { this.dictType = dictType; }

    public String getCssClass() { return cssClass; }
    public void setCssClass(String cssClass) { this.cssClass = cssClass; }

    public String getListClass() { return listClass; }
    public void setListClass(String listClass) { this.listClass = listClass; }

    public String getIsDefault() { return isDefault; }
    public void setIsDefault(String isDefault) { this.isDefault = isDefault; }

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

    /** Row → SysDictData */
    public static SysDictData toSysDictData(Row row) {
        SysDictData d = new SysDictData();
        d.setDictCode(row.getLong("dict_code"));
        d.setDictSort(row.getInteger("dict_sort"));
        d.setDictLabel(row.getString("dict_label"));
        d.setDictValue(row.getString("dict_value"));
        d.setDictType(row.getString("dict_type"));
        d.setCssClass(row.getString("css_class"));
        d.setListClass(row.getString("list_class"));
        d.setIsDefault(row.getString("is_default"));
        d.setStatus(row.getString("status"));
        d.setCreateBy(row.getString("create_by"));
        d.setCreateTime(row.getLocalDateTime("create_time"));
        d.setUpdateBy(row.getString("update_by"));
        d.setUpdateTime(row.getLocalDateTime("update_time"));
        d.setRemark(row.getString("remark"));
        return d;
    }

    /** JsonObject → SysDictData (from API request body) */
    public static SysDictData fromJson(JsonObject json) {
        SysDictData d = new SysDictData();
        d.setDictCode(json.getLong("dictCode"));
        d.setDictSort(json.getInteger("dictSort", 0));
        d.setDictLabel(json.getString("dictLabel"));
        d.setDictValue(json.getString("dictValue"));
        d.setDictType(json.getString("dictType"));
        d.setCssClass(json.getString("cssClass"));
        d.setListClass(json.getString("listClass"));
        d.setIsDefault(json.getString("isDefault", "N"));
        d.setStatus(json.getString("status", "0"));
        d.setCreateBy(json.getString("createBy", "admin"));
        d.setUpdateBy(json.getString("updateBy"));
        d.setRemark(json.getString("remark"));
        return d;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (dictCode != null) json.put("dictCode", dictCode);
        if (dictSort != null) json.put("dictSort", dictSort);
        if (dictLabel != null) json.put("dictLabel", dictLabel);
        if (dictValue != null) json.put("dictValue", dictValue);
        if (dictType != null) json.put("dictType", dictType);
        if (cssClass != null) json.put("cssClass", cssClass);
        if (listClass != null) json.put("listClass", listClass);
        if (isDefault != null) json.put("isDefault", isDefault);
        if (status != null) json.put("status", status);
        if (createBy != null) json.put("createBy", createBy);
        if (createTime != null) json.put("createTime", createTime.toString());
        if (updateBy != null) json.put("updateBy", updateBy);
        if (updateTime != null) json.put("updateTime", updateTime.toString());
        if (remark != null) json.put("remark", remark);
        return json;
    }
}