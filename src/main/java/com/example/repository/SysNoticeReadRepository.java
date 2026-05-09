package com.example.repository;

import com.example.db.DatabaseVerticle;
import com.example.entity.SysNoticeRead;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

import java.util.ArrayList;
import java.util.List;

/**
 * SysNoticeRead Repository - Database operations for sys_notice_read table
 *
 * 对应 RuoYi SysNoticeReadMapper，提供相同的 SQL 功能：
 * - insertNoticeRead            → insert
 * - selectUnreadCount           → countUnread
 * - selectIsRead                → isRead
 * - insertNoticeReadBatch       → insertBatch
 * - selectNoticeListWithReadStatus → findNoticesWithReadStatus
 * - selectReadUsersByNoticeId   → findReadUsers
 * - deleteByNoticeIds           → deleteByNoticeIds
 */
public class SysNoticeReadRepository {

    private final Vertx vertx;

    public SysNoticeReadRepository(Vertx vertx) {
        this.vertx = vertx;
    }

    // ================================================================
    // Row → Entity converters
    // ================================================================

    private SysNoticeRead toEntity(Row row) {
        return SysNoticeRead.fromRow(row);
    }

    private List<SysNoticeRead> toList(RowSet<Row> rows) {
        List<SysNoticeRead> list = new ArrayList<>();
        for (Row row : rows) {
            list.add(toEntity(row));
        }
        return list;
    }

    // ================================================================
    // INSERT OPERATIONS
    // ================================================================

    /**
     * 新增已读记录（忽略重复）
     * 对应 RuoYi insertNoticeRead
     *
     * 使用 ON CONFLICT DO NOTHING 避免唯一约束冲突
     *
     * @param noticeRead 已读记录
     * @return 插入行数（1=成功，0=已存在）
     */
    public Future<Integer> insert(SysNoticeRead noticeRead) {
        String sql = """
            INSERT INTO sys_notice_read (notice_id, user_id, read_time)
            VALUES ($1, $2, CURRENT_TIMESTAMP)
            ON CONFLICT (user_id, notice_id) DO NOTHING
            """;
        Tuple params = Tuple.tuple()
            .addLong(noticeRead.getNoticeId())
            .addLong(noticeRead.getUserId());
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.rowCount());
    }

    /**
     * 批量标记已读
     * 对应 RuoYi insertNoticeReadBatch
     *
     * 使用 INSERT ... ON CONFLICT DO NOTHING 批量插入
     *
     * @param userId    用户ID
     * @param noticeIds 公告ID数组
     * @return 成功插入行数
     */
    public Future<Integer> insertBatch(Long userId, Long[] noticeIds) {
        if (noticeIds == null || noticeIds.length == 0) {
            return Future.succeededFuture(0);
        }

        // PostgreSQL 不支持单条 INSERT 多 VALUES RETURNING 聚合计数
        // 使用 VALUES (...), (...), ... 批量插入，ON CONFLICT DO NOTHING
        StringBuilder sql = new StringBuilder(
            "INSERT INTO sys_notice_read (notice_id, user_id, read_time) VALUES ");
        Tuple params = Tuple.tuple();
        for (int i = 0; i < noticeIds.length; i++) {
            if (i > 0) sql.append(", ");
            sql.append("($").append(i * 2 + 1).append(", $").append(i * 2 + 2).append(", CURRENT_TIMESTAMP)");
            params.addLong(noticeIds[i]).addLong(userId);
        }
        sql.append(" ON CONFLICT (user_id, notice_id) DO NOTHING");

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(rows -> rows.rowCount());
    }

    // ================================================================
    // QUERY OPERATIONS
    // ================================================================

    /**
     * 查询某用户未读公告数量
     * 对应 RuoYi selectUnreadCount
     *
     * @param userId 用户ID
     * @return 未读数量
     */
    public Future<Long> countUnread(Long userId) {
        String sql = """
            SELECT COUNT(*) AS cnt
            FROM sys_notice n
            WHERE n.status = '0'
              AND NOT EXISTS (
                SELECT 1 FROM sys_notice_read r
                WHERE r.notice_id = n.notice_id AND r.user_id = $1
              )
            """;
        Tuple params = Tuple.tuple().addLong(userId);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getLong("cnt"));
    }

    /**
     * 查询某用户是否已读某公告
     * 对应 RuoYi selectIsRead
     *
     * @param noticeId 公告ID
     * @param userId   用户ID
     * @return true=已读，false=未读
     */
    public Future<Boolean> isRead(Long noticeId, Long userId) {
        String sql = "SELECT 1 FROM sys_notice_read WHERE notice_id = $1 AND user_id = $2";
        Tuple params = Tuple.tuple()
            .addLong(noticeId)
            .addLong(userId);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.size() > 0);
    }

    /**
     * 查询带已读状态的公告列表（SQL层限制条数，一次查询完成）
     * 对应 RuoYi selectNoticeListWithReadStatus
     *
     * 使用 LEFT JOIN 关联 sys_notice_read，判断 isRead 标记
     *
     * @param userId 用户ID
     * @param limit  最多返回条数
     * @return 带 isRead 标记的公告列表（JsonObject 形式）
     */
    public Future<List<JsonObject>> findNoticesWithReadStatus(Long userId, int limit) {
        String sql = """
            SELECT n.notice_id, n.notice_title, n.notice_type, n.notice_content,
                   n.status, n.create_by, n.create_time, n.update_by, n.update_time, n.remark,
                   r.notice_id AS read_notice_id
            FROM sys_notice n
            LEFT JOIN sys_notice_read r
              ON n.notice_id = r.notice_id AND r.user_id = $1
            WHERE n.status = '0'
            ORDER BY n.notice_id DESC
            LIMIT $2
            """;
        Tuple params = Tuple.tuple()
            .addLong(userId)
            .addInteger(limit);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<JsonObject> list = new ArrayList<>();
                for (Row row : rows) {
                    list.add(SysNoticeRead.fromRowAsNoticeWithReadFlag(row));
                }
                return list;
            });
    }

    /**
     * 查询已阅读某公告的用户列表
     * 对应 RuoYi selectReadUsersByNoticeId
     *
     * @param noticeId    公告ID
     * @param searchValue 搜索值（可选，匹配用户名）
     * @return 已读用户列表（Map 形式：userId, userName, readTime）
     */
    public Future<List<JsonObject>> findReadUsers(Long noticeId, String searchValue) {
        StringBuilder sql = new StringBuilder("""
            SELECT r.user_id, u.user_name, r.read_time
            FROM sys_notice_read r
            LEFT JOIN sys_user u ON r.user_id = u.user_id
            WHERE r.notice_id = $1
            """);
        Tuple params = Tuple.tuple().addLong(noticeId);
        int idx = 2;

        if (searchValue != null && !searchValue.isEmpty()) {
            sql.append(" AND u.user_name LIKE $").append(idx++);
            params.addString("%" + searchValue + "%");
        }
        sql.append(" ORDER BY r.read_time DESC");

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(rows -> {
                List<JsonObject> list = new ArrayList<>();
                for (Row row : rows) {
                    JsonObject json = new JsonObject()
                        .put("userId", row.getLong("user_id"))
                        .put("userName", row.getString("user_name"));
                    Object rt = row.getValue("read_time");
                    if (rt != null) json.put("readTime", rt.toString());
                    list.add(json);
                }
                return list;
            });
    }

    // ================================================================
    // DELETE OPERATIONS
    // ================================================================

    /**
     * 公告删除时清理对应已读记录
     * 对应 RuoYi deleteByNoticeIds
     *
     * @param noticeIds 公告ID数组
     * @return 删除行数
     */
    public Future<Integer> deleteByNoticeIds(Long[] noticeIds) {
        if (noticeIds == null || noticeIds.length == 0) {
            return Future.succeededFuture(0);
        }
        StringBuilder sql = new StringBuilder("DELETE FROM sys_notice_read WHERE notice_id IN (");
        Tuple params = Tuple.tuple();
        for (int i = 0; i < noticeIds.length; i++) {
            if (i > 0) sql.append(", ");
            sql.append("$").append(i + 1);
            params.addLong(noticeIds[i]);
        }
        sql.append(")");
        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(rows -> rows.rowCount());
    }
}
