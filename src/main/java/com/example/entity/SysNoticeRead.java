package com.example.entity;

import java.time.LocalDateTime;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;

/**
 * 公告已读记录表 sys_notice_read
 */
public class SysNoticeRead {

    private Long readId;
    private Long noticeId;
    private Long userId;
    private LocalDateTime readTime;

    public SysNoticeRead() {}

    public SysNoticeRead(Long noticeId, Long userId) {
        this.noticeId = noticeId;
        this.userId = userId;
    }

    // --- Getters & Setters ---
    public Long getReadId() { return readId; }
    public void setReadId(Long readId) { this.readId = readId; }

    public Long getNoticeId() { return noticeId; }
    public void setNoticeId(Long noticeId) { this.noticeId = noticeId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public LocalDateTime getReadTime() { return readTime; }
    public void setReadTime(LocalDateTime readTime) { this.readTime = readTime; }

    // --- Conversions ---
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (readId != null)    json.put("readId",    readId);
        if (noticeId != null) json.put("noticeId",  noticeId);
        if (userId != null)   json.put("userId",    userId);
        if (readTime != null) json.put("readTime",  readTime.toString());
        return json;
    }

    public static SysNoticeRead fromJson(JsonObject json) {
        if (json == null) return null;
        SysNoticeRead r = new SysNoticeRead();
        r.setReadId(json.getLong("readId") != null ? json.getLong("readId") : json.getLong("read_id"));
        r.setNoticeId(json.getLong("noticeId") != null ? json.getLong("noticeId") : json.getLong("notice_id"));
        r.setUserId(json.getLong("userId") != null ? json.getLong("userId") : json.getLong("user_id"));
        String rt = json.getString("readTime") != null ? json.getString("readTime") : json.getString("read_time");
        if (rt != null) r.setReadTime(LocalDateTime.parse(rt));
        return r;
    }

    public static SysNoticeRead fromRow(Row row) {
        if (row == null) return null;
        SysNoticeRead r = new SysNoticeRead();
        r.setReadId(row.getLong("read_id"));
        r.setNoticeId(row.getLong("notice_id"));
        r.setUserId(row.getLong("user_id"));
        r.setReadTime(row.getLocalDateTime("read_time"));
        return r;
    }

    /**
     * 将 join 查询行映射为 SysNotice + isRead 标记的 JsonObject
     * 用于 selectNoticeListWithReadStatus 的结果映射
     */
    public static JsonObject fromRowAsNoticeWithReadFlag(Row row) {
        JsonObject json = new JsonObject();
        json.put("noticeId",      row.getLong("notice_id"));
        json.put("noticeTitle",   row.getString("notice_title"));
        json.put("noticeType",    row.getString("notice_type"));
        json.put("noticeContent", row.getString("notice_content"));
        json.put("status",       row.getString("status"));
        json.put("createBy",     row.getString("create_by"));
        Object ct = row.getValue("create_time");
        if (ct != null) json.put("createTime", ct.toString());
        json.put("updateBy",     row.getString("update_by"));
        Object ut = row.getValue("update_time");
        if (ut != null) json.put("updateTime", ut.toString());
        json.put("remark",       row.getString("remark"));
        // isRead 标记：LEFT JOIN 后 notice_id 非空即已读
        json.put("isRead", row.getLong("read_notice_id") != null);
        return json;
    }
}