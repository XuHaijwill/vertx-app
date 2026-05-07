package com.example.entity;

import java.time.LocalDateTime;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;

/**
 * System role entity — equivalent to RuoYi's SysRole.
 *
 * <p>Represents a named role (e.g. "admin", "common") that can be assigned
 * to users. Each role has a unique role_key used in permission checks.</p>
 *
 * <p>Permission checks:</p>
 * <ul>
 *   <li>{@code AuthUtils.hasRole(ctx, "admin")} — is the current user admin?</li>
 *   <li>{@code RequirePermission.ofRole("admin")} — route guard for admin only</li>
 * </ul>
 */
public class SysRole {

    private Long roleId;
    private String roleName;
    private String roleKey;
    private Integer roleSort;
    private String dataScope;
    private Boolean menuCheckStrictly;
    private Boolean deptCheckStrictly;
    private String status;     // '0'=Normal, '1'=Disabled
    private String delFlag;    // '0'=Exist, '2'=Deleted
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private String remark;

    /** Super admin role key */
    public static final String ADMIN_KEY = "admin";
    /** Common user role key */
    public static final String COMMON_KEY = "common";
    /** Normal status */
    public static final String STATUS_NORMAL = "0";
    /** Disabled status */
    public static final String STATUS_DISABLED = "1";

    public SysRole() {}

    // ========== Row mapping ==========

    public static SysRole fromRow(Row row) {
        if (row == null) return null;
        SysRole r = new SysRole();
        r.roleId = row.getLong("role_id");
        r.roleName = row.getString("role_name");
        r.roleKey = row.getString("role_key");
        r.roleSort = row.getInteger("role_sort");
        r.dataScope = row.getString("data_scope");
        r.menuCheckStrictly = row.getBoolean("menu_check_strictly");
        r.deptCheckStrictly = row.getBoolean("dept_check_strictly");
        r.status = row.getString("status");
        r.delFlag = row.getString("del_flag");
        r.createBy = row.getString("create_by");
        r.createTime = row.getLocalDateTime("create_time");
        r.updateBy = row.getString("update_by");
        r.updateTime = row.getLocalDateTime("update_time");
        r.remark = row.getString("remark");
        return r;
    }

    public static JsonObject toJson(SysRole r) {
        if (r == null) return null;
        JsonObject json = new JsonObject();
        json.put("roleId", r.roleId);
        if (r.roleName != null) json.put("roleName", r.roleName);
        if (r.roleKey != null) json.put("roleKey", r.roleKey);
        if (r.roleSort != null) json.put("roleSort", r.roleSort);
        if (r.dataScope != null) json.put("dataScope", r.dataScope);
        if (r.menuCheckStrictly != null) json.put("menuCheckStrictly", r.menuCheckStrictly);
        if (r.deptCheckStrictly != null) json.put("deptCheckStrictly", r.deptCheckStrictly);
        if (r.status != null) json.put("status", r.status);
        if (r.delFlag != null) json.put("delFlag", r.delFlag);
        if (r.createBy != null) json.put("createBy", r.createBy);
        if (r.createTime != null) json.put("createTime", r.createTime.toString());
        if (r.updateBy != null) json.put("updateBy", r.updateBy);
        if (r.updateTime != null) json.put("updateTime", r.updateTime.toString());
        if (r.remark != null) json.put("remark", r.remark);
        return json;
    }

    // ========== Getters & Setters ==========

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public String getRoleKey() { return roleKey; }
    public void setRoleKey(String roleKey) { this.roleKey = roleKey; }

    public Integer getRoleSort() { return roleSort; }
    public void setRoleSort(Integer roleSort) { this.roleSort = roleSort; }

    public String getDataScope() { return dataScope; }
    public void setDataScope(String dataScope) { this.dataScope = dataScope; }

    public Boolean getMenuCheckStrictly() { return menuCheckStrictly; }
    public void setMenuCheckStrictly(Boolean menuCheckStrictly) { this.menuCheckStrictly = menuCheckStrictly; }

    public Boolean getDeptCheckStrictly() { return deptCheckStrictly; }
    public void setDeptCheckStrictly(Boolean deptCheckStrictly) { this.deptCheckStrictly = deptCheckStrictly; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }

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

    public boolean isAdmin() {
        return ADMIN_KEY.equals(roleKey);
    }

    public boolean isNormal() {
        return STATUS_NORMAL.equals(status);
    }

    public JsonObject toJson() {
        return toJson(this);
    }
}
