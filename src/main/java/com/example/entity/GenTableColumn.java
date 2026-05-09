package com.example.entity;

import java.time.LocalDateTime;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;

/**
 * 代码生成业务字段表 gen_table_column
 */
public class GenTableColumn {

    private Long columnId;
    private Long tableId;
    private String columnName;
    private String columnComment;
    private String columnType;
    private String javaType;
    private String javaField;
    private String isPk;
    private String isIncrement;
    private String isRequired;
    private String isInsert;
    private String isEdit;
    private String isList;
    private String isQuery;
    private String queryType;
    private String htmlType;
    private String dictType;
    private Integer sort;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;

    public GenTableColumn() {}

    // --- Getters & Setters ---
    public Long getColumnId() { return columnId; }
    public void setColumnId(Long columnId) { this.columnId = columnId; }

    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }

    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }

    public String getColumnComment() { return columnComment; }
    public void setColumnComment(String columnComment) { this.columnComment = columnComment; }

    public String getColumnType() { return columnType; }
    public void setColumnType(String columnType) { this.columnType = columnType; }

    public String getJavaType() { return javaType; }
    public void setJavaType(String javaType) { this.javaType = javaType; }

    public String getJavaField() { return javaField; }
    public void setJavaField(String javaField) { this.javaField = javaField; }

    public String getIsPk() { return isPk; }
    public void setIsPk(String isPk) { this.isPk = isPk; }

    public String getIsIncrement() { return isIncrement; }
    public void setIsIncrement(String isIncrement) { this.isIncrement = isIncrement; }

    public String getIsRequired() { return isRequired; }
    public void setIsRequired(String isRequired) { this.isRequired = isRequired; }

    public String getIsInsert() { return isInsert; }
    public void setIsInsert(String isInsert) { this.isInsert = isInsert; }

    public String getIsEdit() { return isEdit; }
    public void setIsEdit(String isEdit) { this.isEdit = isEdit; }

    public String getIsList() { return isList; }
    public void setIsList(String isList) { this.isList = isList; }

    public String getIsQuery() { return isQuery; }
    public void setIsQuery(String isQuery) { this.isQuery = isQuery; }

    public String getQueryType() { return queryType; }
    public void setQueryType(String queryType) { this.queryType = queryType; }

    public String getHtmlType() { return htmlType; }
    public void setHtmlType(String htmlType) { this.htmlType = htmlType; }

    public String getDictType() { return dictType; }
    public void setDictType(String dictType) { this.dictType = dictType; }

    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }

    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    // --- Helper methods ---
    public boolean isPk() {
        return "1".equals(isPk);
    }

    public boolean isIncrement() {
        return "1".equals(isIncrement);
    }

    public boolean isRequired() {
        return "1".equals(isRequired);
    }

    // --- Conversions ---
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (columnId != null) json.put("columnId", columnId);
        if (tableId != null) json.put("tableId", tableId);
        if (columnName != null) json.put("columnName", columnName);
        if (columnComment != null) json.put("columnComment", columnComment);
        if (columnType != null) json.put("columnType", columnType);
        if (javaType != null) json.put("javaType", javaType);
        if (javaField != null) json.put("javaField", javaField);
        if (isPk != null) json.put("isPk", isPk);
        if (isIncrement != null) json.put("isIncrement", isIncrement);
        if (isRequired != null) json.put("isRequired", isRequired);
        if (isInsert != null) json.put("isInsert", isInsert);
        if (isEdit != null) json.put("isEdit", isEdit);
        if (isList != null) json.put("isList", isList);
        if (isQuery != null) json.put("isQuery", isQuery);
        if (queryType != null) json.put("queryType", queryType);
        if (htmlType != null) json.put("htmlType", htmlType);
        if (dictType != null) json.put("dictType", dictType);
        if (sort != null) json.put("sort", sort);
        if (createBy != null) json.put("createBy", createBy);
        if (createTime != null) json.put("createTime", createTime.toString());
        if (updateBy != null) json.put("updateBy", updateBy);
        if (updateTime != null) json.put("updateTime", updateTime.toString());
        return json;
    }

    public static GenTableColumn fromRow(Row row) {
        if (row == null) return null;
        GenTableColumn c = new GenTableColumn();
        c.setColumnId(row.getLong("column_id"));
        c.setTableId(row.getLong("table_id"));
        c.setColumnName(row.getString("column_name"));
        c.setColumnComment(row.getString("column_comment"));
        c.setColumnType(row.getString("column_type"));
        c.setJavaType(row.getString("java_type"));
        c.setJavaField(row.getString("java_field"));
        c.setIsPk(row.getString("is_pk"));
        c.setIsIncrement(row.getString("is_increment"));
        c.setIsRequired(row.getString("is_required"));
        c.setIsInsert(row.getString("is_insert"));
        c.setIsEdit(row.getString("is_edit"));
        c.setIsList(row.getString("is_list"));
        c.setIsQuery(row.getString("is_query"));
        c.setQueryType(row.getString("query_type"));
        c.setHtmlType(row.getString("html_type"));
        c.setDictType(row.getString("dict_type"));
        c.setSort(row.getInteger("sort"));
        c.setCreateBy(row.getString("create_by"));
        c.setCreateTime(row.getLocalDateTime("create_time"));
        c.setUpdateBy(row.getString("update_by"));
        c.setUpdateTime(row.getLocalDateTime("update_time"));
        return c;
    }
}
