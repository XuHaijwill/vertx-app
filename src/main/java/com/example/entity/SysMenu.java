package com.example.entity;

import java.time.LocalDateTime;
import java.util.List;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;

public class SysMenu {
    private Long menuId;
    private String menuName;
    private Long parentId;
    private Integer orderNum;
    private String path;
    private String component;
    private String query;
    private String routeName;
    private Integer isFrame;
    private Integer isCache;
    private String menuType;
    private String visible;
    private String status;
    private String perms;
    private String icon;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private String remark;

    public SysMenu() {}

    public SysMenu(Long menuId, String menuName, Long parentId, String menuType) {
        this.menuId = menuId;
        this.menuName = menuName;
        this.parentId = parentId;
        this.menuType = menuType;
    }

    public Long getMenuId() { return menuId; }
    public void setMenuId(Long menuId) { this.menuId = menuId; }

    public String getMenuName() { return menuName; }
    public void setMenuName(String menuName) { this.menuName = menuName; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

    public Integer getOrderNum() { return orderNum; }
    public void setOrderNum(Integer orderNum) { this.orderNum = orderNum; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getComponent() { return component; }
    public void setComponent(String component) { this.component = component; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getRouteName() { return routeName; }
    public void setRouteName(String routeName) { this.routeName = routeName; }

    public Integer getIsFrame() { return isFrame; }
    public void setIsFrame(Integer isFrame) { this.isFrame = isFrame; }

    public Integer getIsCache() { return isCache; }
    public void setIsCache(Integer isCache) { this.isCache = isCache; }

    public String getMenuType() { return menuType; }
    public void setMenuType(String menuType) { this.menuType = menuType; }

    public String getVisible() { return visible; }
    public void setVisible(String visible) { this.visible = visible; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPerms() { return perms; }
    public void setPerms(String perms) { this.perms = perms; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

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

    // ================================================================
    // Static factory methods
    // ================================================================

    /**
     * Build SysMenu from a database Row
     */
    public static SysMenu toSysMenu(Row row) {
        SysMenu m = new SysMenu();
        m.menuId = row.getLong("menu_id");
        m.menuName = row.getString("menu_name");
        m.parentId = row.getLong("parent_id");
        m.orderNum = row.getInteger("order_num");
        m.path = row.getString("path");
        m.component = row.getString("component");
        m.query = row.getString("query");
        m.routeName = row.getString("route_name");
        m.isFrame = row.getInteger("is_frame");
        m.isCache = row.getInteger("is_cache");
        m.menuType = row.getString("menu_type");
        m.visible = row.getString("visible");
        m.status = row.getString("status");
        m.perms = row.getString("perms");
        m.icon = row.getString("icon");
        m.createBy = row.getString("create_by");
        m.createTime = row.getLocalDateTime("create_time");
        m.updateBy = row.getString("update_by");
        m.updateTime = row.getLocalDateTime("update_time");
        m.remark = row.getString("remark");
        return m;
    }

    /**
     * Build SysMenu from a JsonObject (e.g. HTTP request body)
     */
    public static SysMenu fromJson(JsonObject json) {
        SysMenu m = new SysMenu();
        m.menuId = json.getLong("menuId");
        m.menuName = json.getString("menuName");
        m.parentId = json.getLong("parentId", 0L);
        m.orderNum = json.getInteger("orderNum", 0);
        m.path = json.getString("path", "");
        m.component = json.getString("component");
        m.query = json.getString("query");
        m.routeName = json.getString("routeName", "");
        m.isFrame = json.getInteger("isFrame", 1);
        m.isCache = json.getInteger("isCache", 0);
        m.menuType = json.getString("menuType", "M");
        m.visible = json.getString("visible", "0");
        m.status = json.getString("status", "0");
        m.perms = json.getString("perms");
        m.icon = json.getString("icon", "#");
        m.createBy = json.getString("createBy", "admin");
        m.updateBy = json.getString("updateBy");
        m.remark = json.getString("remark", "");
        return m;
    }

    private List<SysMenu> children;
    public List<SysMenu> getChildren() { return children; }
    public void setChildren(List<SysMenu> children) { this.children = children; }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (menuId != null) json.put("menuId", menuId);
        if (menuName != null) json.put("menuName", menuName);
        if (parentId != null) json.put("parentId", parentId);
        if (orderNum != null) json.put("orderNum", orderNum);
        if (path != null) json.put("path", path);
        if (component != null) json.put("component", component);
        if (query != null) json.put("query", query);
        if (routeName != null) json.put("routeName", routeName);
        if (isFrame != null) json.put("isFrame", isFrame);
        if (isCache != null) json.put("isCache", isCache);
        if (menuType != null) json.put("menuType", menuType);
        if (visible != null) json.put("visible", visible);
        if (status != null) json.put("status", status);
        if (perms != null) json.put("perms", perms);
        if (icon != null) json.put("icon", icon);
        if (createBy != null) json.put("createBy", createBy);
        if (createTime != null) json.put("createTime", createTime.toString());
        if (updateBy != null) json.put("updateBy", updateBy);
        if (updateTime != null) json.put("updateTime", updateTime.toString());
        if (remark != null) json.put("remark", remark);
        if (children != null) {
            json.put("children", children.stream().map(SysMenu::toJson).collect(java.util.stream.Collectors.toList()));
        }
        return json;
    }
}