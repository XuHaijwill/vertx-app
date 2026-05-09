package com.example.entity;

import java.time.LocalDateTime;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;

/**
 * 通知公告表 sys_notice
 */
public class SysNotice {

    private Long noticeId;
    private String noticeTitle;
    private String noticeType;
    private String noticeContent;
    private String status;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private String remark;

    public SysNotice() {}

    // --- Getters & Setters ---
    public Long getNoticeId() { return noticeId; }
    public void setNoticeId(Long noticeId) { this.noticeId = noticeId; }

    public String getNoticeTitle() { return noticeTitle; }
    public void setNoticeTitle(String noticeTitle) { this.noticeTitle = noticeTitle; }

    public String getNoticeType() { return noticeType; }
    public void setNoticeType(String noticeType) { this.noticeType = noticeType; }

    public String getNoticeContent() { return noticeContent; }
    public void setNoticeContent(String noticeContent) { this.noticeContent = noticeContent; }

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

    // --- Conversions ---
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (noticeId != null)      json.put("noticeId",      noticeId);
        if (noticeTitle != null)   json.put("noticeTitle",   noticeTitle);
        if (noticeType != null)    json.put("noticeType",    noticeType);
        if (noticeContent != null) json.put("noticeContent", noticeContent);
        if (status != null)       json.put("status",        status);
        if (createBy != null)      json.put("createBy",      createBy);
        if (createTime != null)   json.put("createTime",    createTime.toString());
        if (updateBy != null)      json.put("updateBy",      updateBy);
        if (updateTime != null)   json.put("updateTime",    updateTime.toString());
        if (remark != null)       json.put("remark",        remark);
        return json;
    }

    public static SysNotice fromJson(JsonObject json) {
        if (json == null) return null;
        SysNotice n = new SysNotice();
        n.setNoticeId(json.getLong("noticeId") != null ? json.getLong("noticeId") : json.getLong("notice_id"));
        n.setNoticeTitle(json.getString("noticeTitle") != null ? json.getString("noticeTitle") : json.getString("notice_title"));
        n.setNoticeType(json.getString("noticeType") != null ? json.getString("noticeType") : json.getString("notice_type"));
        n.setNoticeContent(json.getString("noticeContent") != null ? json.getString("noticeContent") : json.getString("notice_content"));
        n.setStatus(json.getString("status"));
        n.setCreateBy(json.getString("createBy") != null ? json.getString("createBy") : json.getString("create_by"));
        String ct = json.getString("createTime") != null ? json.getString("createTime") : json.getString("create_time");
        if (ct != null) n.setCreateTime(LocalDateTime.parse(ct));
        n.setUpdateBy(json.getString("updateBy") != null ? json.getString("updateBy") : json.getString("update_by"));
        String ut = json.getString("updateTime") != null ? json.getString("updateTime") : json.getString("update_time");
        if (ut != null) n.setUpdateTime(LocalDateTime.parse(ut));
        n.setRemark(json.getString("remark"));
        return n;
    }

    public static SysNotice fromRow(Row row) {
        if (row == null) return null;
        SysNotice n = new SysNotice();
        n.setNoticeId(row.getLong("notice_id"));
        n.setNoticeTitle(row.getString("notice_title"));
        n.setNoticeType(row.getString("notice_type"));
        n.setNoticeContent(row.getString("notice_content"));
        n.setStatus(row.getString("status"));
        n.setCreateBy(row.getString("create_by"));
        n.setCreateTime(row.getLocalDateTime("create_time"));
        n.setUpdateBy(row.getString("update_by"));
        n.setUpdateTime(row.getLocalDateTime("update_time"));
        n.setRemark(row.getString("remark"));
        return n;
    }

    public static SysNotice toNoticeOne(JsonObject json) { return fromJson(json); }

    public static java.util.List<SysNotice> toNoticeList(java.util.List<JsonObject> list) {
        if (list == null) return java.util.Collections.emptyList();
        return list.stream().map(SysNotice::fromJson).collect(java.util.stream.Collectors.toList());
    }
}
