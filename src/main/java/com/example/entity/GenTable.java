package com.example.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;

/**
 * 代码生成业务表 gen_table
 */
public class GenTable {

    private Long tableId;
    private String tableName;
    private String tableComment;
    private String subTableName;
    private String subTableFkName;
    private String className;
    private String tplCategory;
    private String tplWebType;
    private String packageName;
    private String moduleName;
    private String businessName;
    private String functionName;
    private String functionAuthor;
    private Integer formColNum;
    private String genType;
    private String genPath;
    private String options;
    private String treeCode;
    private String treeParentCode;
    private String treeName;
    private Long parentMenuId;
    private String parentMenuName;
    private Boolean isView;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private String remark;

    // 关联字段
    private GenTableColumn pkColumn;
    private GenTable subTable;
    private List<GenTableColumn> columns;

    public GenTable() {
        this.columns = new ArrayList<>();
    }

    // --- Getters & Setters ---
    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public String getTableComment() { return tableComment; }
    public void setTableComment(String tableComment) { this.tableComment = tableComment; }

    public String getSubTableName() { return subTableName; }
    public void setSubTableName(String subTableName) { this.subTableName = subTableName; }

    public String getSubTableFkName() { return subTableFkName; }
    public void setSubTableFkName(String subTableFkName) { this.subTableFkName = subTableFkName; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getTplCategory() { return tplCategory; }
    public void setTplCategory(String tplCategory) { this.tplCategory = tplCategory; }

    public String getTplWebType() { return tplWebType; }
    public void setTplWebType(String tplWebType) { this.tplWebType = tplWebType; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public String getModuleName() { return moduleName; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }

    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }

    public String getFunctionName() { return functionName; }
    public void setFunctionName(String functionName) { this.functionName = functionName; }

    public String getFunctionAuthor() { return functionAuthor; }
    public void setFunctionAuthor(String functionAuthor) { this.functionAuthor = functionAuthor; }

    public Integer getFormColNum() { return formColNum; }
    public void setFormColNum(Integer formColNum) { this.formColNum = formColNum; }

    public String getGenType() { return genType; }
    public void setGenType(String genType) { this.genType = genType; }

    public String getGenPath() { return genPath; }
    public void setGenPath(String genPath) { this.genPath = genPath; }

    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }

    public String getTreeCode() { return treeCode; }
    public void setTreeCode(String treeCode) { this.treeCode = treeCode; }

    public String getTreeParentCode() { return treeParentCode; }
    public void setTreeParentCode(String treeParentCode) { this.treeParentCode = treeParentCode; }

    public String getTreeName() { return treeName; }
    public void setTreeName(String treeName) { this.treeName = treeName; }

    public Long getParentMenuId() { return parentMenuId; }
    public void setParentMenuId(Long parentMenuId) { this.parentMenuId = parentMenuId; }

    public String getParentMenuName() { return parentMenuName; }
    public void setParentMenuName(String parentMenuName) { this.parentMenuName = parentMenuName; }

    public Boolean getIsView() { return isView; }
    public void setIsView(Boolean isView) { this.isView = isView; }

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

    public GenTableColumn getPkColumn() { return pkColumn; }
    public void setPkColumn(GenTableColumn pkColumn) { this.pkColumn = pkColumn; }

    public GenTable getSubTable() { return subTable; }
    public void setSubTable(GenTable subTable) { this.subTable = subTable; }

    public List<GenTableColumn> getColumns() { return columns; }
    public void setColumns(List<GenTableColumn> columns) { this.columns = columns != null ? columns : new ArrayList<>(); }

    // --- Conversions ---
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (tableId != null) json.put("tableId", tableId);
        if (tableName != null) json.put("tableName", tableName);
        if (tableComment != null) json.put("tableComment", tableComment);
        if (subTableName != null) json.put("subTableName", subTableName);
        if (subTableFkName != null) json.put("subTableFkName", subTableFkName);
        if (className != null) json.put("className", className);
        if (tplCategory != null) json.put("tplCategory", tplCategory);
        if (tplWebType != null) json.put("tplWebType", tplWebType);
        if (packageName != null) json.put("packageName", packageName);
        if (moduleName != null) json.put("moduleName", moduleName);
        if (businessName != null) json.put("businessName", businessName);
        if (functionName != null) json.put("functionName", functionName);
        if (functionAuthor != null) json.put("functionAuthor", functionAuthor);
        if (formColNum != null) json.put("formColNum", formColNum);
        if (genType != null) json.put("genType", genType);
        if (genPath != null) json.put("genPath", genPath);
        if (options != null) json.put("options", options);
        if (treeCode != null) json.put("treeCode", treeCode);
        if (treeParentCode != null) json.put("treeParentCode", treeParentCode);
        if (treeName != null) json.put("treeName", treeName);
        if (parentMenuId != null) json.put("parentMenuId", parentMenuId);
        if (parentMenuName != null) json.put("parentMenuName", parentMenuName);
        if (isView != null) json.put("isView", isView);
        if (createBy != null) json.put("createBy", createBy);
        if (createTime != null) json.put("createTime", createTime.toString());
        if (updateBy != null) json.put("updateBy", updateBy);
        if (updateTime != null) json.put("updateTime", updateTime.toString());
        if (remark != null) json.put("remark", remark);
        if (columns != null && !columns.isEmpty()) {
            JsonArray colsArr = new JsonArray();
            columns.forEach(c -> colsArr.add(c.toJson()));
            json.put("columns", colsArr);
        }
        return json;
    }

    public static GenTable fromRow(Row row) {
        if (row == null) return null;
        GenTable t = new GenTable();
        t.setTableId(row.getLong("table_id"));
        t.setTableName(row.getString("table_name"));
        t.setTableComment(row.getString("table_comment"));
        t.setSubTableName(row.getString("sub_table_name"));
        t.setSubTableFkName(row.getString("sub_table_fk_name"));
        t.setClassName(row.getString("class_name"));
        t.setTplCategory(row.getString("tpl_category"));
        t.setTplWebType(row.getString("tpl_web_type"));
        t.setPackageName(row.getString("package_name"));
        t.setModuleName(row.getString("module_name"));
        t.setBusinessName(row.getString("business_name"));
        t.setFunctionName(row.getString("function_name"));
        t.setFunctionAuthor(row.getString("function_author"));
        t.setFormColNum(row.getInteger("form_col_num"));
        t.setGenType(row.getString("gen_type"));
        t.setGenPath(row.getString("gen_path"));
        t.setOptions(row.getString("options"));
        t.setCreateBy(row.getString("create_by"));
        t.setCreateTime(row.getLocalDateTime("create_time"));
        t.setUpdateBy(row.getString("update_by"));
        t.setUpdateTime(row.getLocalDateTime("update_time"));
        t.setRemark(row.getString("remark"));
        return t;
    }

    public static GenTable fromJson(JsonObject json) {
        if (json == null) return null;
        GenTable t = new GenTable();
        t.setTableId(json.getLong("tableId"));
        t.setTableName(json.getString("tableName"));
        t.setTableComment(json.getString("tableComment"));
        t.setSubTableName(json.getString("subTableName"));
        t.setSubTableFkName(json.getString("subTableFkName"));
        t.setClassName(json.getString("className"));
        t.setTplCategory(json.getString("tplCategory"));
        t.setTplWebType(json.getString("tplWebType"));
        t.setPackageName(json.getString("packageName"));
        t.setModuleName(json.getString("moduleName"));
        t.setBusinessName(json.getString("businessName"));
        t.setFunctionName(json.getString("functionName"));
        t.setFunctionAuthor(json.getString("functionAuthor"));
        t.setFormColNum(json.getInteger("formColNum"));
        t.setGenType(json.getString("genType"));
        t.setGenPath(json.getString("genPath"));
        t.setOptions(json.getString("options"));
        t.setTreeCode(json.getString("treeCode"));
        t.setTreeParentCode(json.getString("treeParentCode"));
        t.setTreeName(json.getString("treeName"));
        t.setParentMenuId(json.getLong("parentMenuId"));
        t.setCreateBy(json.getString("createBy"));
        t.setUpdateBy(json.getString("updateBy"));
        t.setRemark(json.getString("remark"));
        return t;
    }
}
