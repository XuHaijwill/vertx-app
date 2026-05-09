package com.example.repository;

import com.example.db.DatabaseVerticle;
import com.example.entity.SysNotice;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

import java.util.ArrayList;
import java.util.List;

/**
 * SysNotice Repository - Database operations for sys_notice table
 *
 * 对应 RuoYi SysNoticeMapper，提供相同的 SQL 功能：
 * - selectNoticeById       → findById
 * - selectNoticeList        → search / searchPaginated
 * - insertNotice           → create
 * - updateNotice           → update
 * - deleteNoticeById       → deleteById
 * - deleteNoticeByIds      → deleteByIds
 */
public class SysNoticeRepository {

    private final Vertx vertx;

    public SysNoticeRepository(Vertx vertx) {
        this.vertx = vertx;
    }

    // ================================================================
    // Row → Entity converters
    // ================================================================

    private SysNotice toNotice(Row row) {
        return SysNotice.fromRow(row);
    }

    private List<SysNotice> toNoticeList(RowSet<Row> rows) {
        List<SysNotice> list = new ArrayList<>();
        for (Row row : rows) {
            list.add(toNotice(row));
        }
        return list;
    }

    // ================================================================
    // QUERY OPERATIONS
    // ================================================================

    /**
     * 查询公告信息（对应 RuoYi selectNoticeById）
     *
     * @param noticeId 公告ID
     * @return 公告信息
     */
    public Future<SysNotice> findById(Long noticeId) {
        String sql = "SELECT * FROM sys_notice WHERE notice_id = $1";
        Tuple params = Tuple.tuple().addLong(noticeId);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<SysNotice> list = toNoticeList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    /**
     * 查询公告列表（对应 RuoYi selectNoticeList）
     * 动态多条件过滤：noticeTitle / noticeType / status
     *
     * @param notice 公告查询条件（可为 null）
     * @return 公告集合
     */
    public Future<List<SysNotice>> search(SysNotice notice) {
        StringBuilder sql = new StringBuilder("SELECT * FROM sys_notice WHERE 1=1");
        Tuple params = Tuple.tuple();
        int idx = 1;

        if (notice != null) {
            if (notice.getNoticeTitle() != null && !notice.getNoticeTitle().isEmpty()) {
                sql.append(" AND notice_title LIKE $").append(idx++);
                params.addString("%" + notice.getNoticeTitle() + "%");
            }
            if (notice.getNoticeType() != null && !notice.getNoticeType().isEmpty()) {
                sql.append(" AND notice_type = $").append(idx++);
                params.addString(notice.getNoticeType());
            }
            if (notice.getStatus() != null && !notice.getStatus().isEmpty()) {
                sql.append(" AND status = $").append(idx++);
                params.addString(notice.getStatus());
            }
        }
        sql.append(" ORDER BY notice_id DESC");

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(this::toNoticeList);
    }

    /**
     * 查询所有公告（无过滤）
     */
    public Future<List<SysNotice>> findAll() {
        String sql = "SELECT * FROM sys_notice ORDER BY notice_id DESC";
        return DatabaseVerticle.query(vertx, sql)
            .map(this::toNoticeList);
    }

    // ================================================================
    // MUTATION OPERATIONS
    // ================================================================

    /**
     * 新增公告（对应 RuoYi insertNotice）
     *
     * @param notice 公告信息
     * @return 插入后的公告（含自增 ID）
     */
    public Future<SysNotice> create(SysNotice notice) {
        String sql = """
            INSERT INTO sys_notice (notice_title, notice_type, notice_content, status, create_by, create_time, remark)
            VALUES ($1, $2, $3, $4, $5, CURRENT_TIMESTAMP, $6)
            RETURNING *
            """;
        Tuple params = Tuple.tuple()
            .addString(notice.getNoticeTitle())
            .addString(notice.getNoticeType())
            .addString(notice.getNoticeContent())
            .addString(notice.getStatus() != null ? notice.getStatus() : "0")
            .addString(notice.getCreateBy() != null ? notice.getCreateBy() : "")
            .addString(notice.getRemark());
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<SysNotice> list = toNoticeList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    /**
     * 修改公告（对应 RuoYi updateNotice）
     *
     * @param notice 公告信息（含 noticeId）
     * @return 更新后的公告
     */
    public Future<SysNotice> update(SysNotice notice) {
        String sql = """
            UPDATE sys_notice
            SET notice_title   = COALESCE($2, notice_title),
                notice_type    = COALESCE($3, notice_type),
                notice_content = COALESCE($4, notice_content),
                status         = COALESCE($5, status),
                update_by      = $6,
                update_time    = CURRENT_TIMESTAMP,
                remark        = COALESCE($7, remark)
            WHERE notice_id = $1
            RETURNING *
            """;
        Tuple params = Tuple.tuple()
            .addLong(notice.getNoticeId())
            .addString(notice.getNoticeTitle())
            .addString(notice.getNoticeType())
            .addString(notice.getNoticeContent())
            .addString(notice.getStatus())
            .addString(notice.getUpdateBy() != null ? notice.getUpdateBy() : "")
            .addString(notice.getRemark());
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<SysNotice> list = toNoticeList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    /**
     * 批量删除公告（对应 RuoYi deleteNoticeByIds）
     *
     * @param noticeIds 需要删除的公告ID数组
     * @return 影响行数
     */
    public Future<Integer> deleteByIds(Long[] noticeIds) {
        if (noticeIds == null || noticeIds.length == 0) {
            return Future.succeededFuture(0);
        }
        StringBuilder sql = new StringBuilder("DELETE FROM sys_notice WHERE notice_id IN (");
        Tuple params = Tuple.tuple();
        for (int i = 0; i < noticeIds.length; i++) {
            sql.append("$").append(i + 1);
            params.addLong(noticeIds[i]);
            if (i < noticeIds.length - 1) sql.append(", ");
        }
        sql.append(")");
        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(rows -> rows.rowCount());
    }

    /**
     * 删除单条公告（对应 RuoYi deleteNoticeById）
     *
     * @param noticeId 公告ID
     * @return void
     */
    public Future<Void> deleteById(Long noticeId) {
        String sql = "DELETE FROM sys_notice WHERE notice_id = $1";
        Tuple params = Tuple.tuple().addLong(noticeId);
        return DatabaseVerticle.query(vertx, sql, params)
            .mapEmpty();
    }

    // ================================================================
    // PAGINATION
    // ================================================================

    /**
     * 分页查询公告列表
     *
     * @param notice 公告查询条件（可为 null）
     * @param page   页码（从 1 开始）
     * @param size   每页条数
     * @return 公告分页列表
     */
    public Future<List<SysNotice>> searchPaginated(SysNotice notice, int page, int size) {
        int offset = (page - 1) * size;
        StringBuilder sql = new StringBuilder("SELECT * FROM sys_notice WHERE 1=1");
        Tuple params = Tuple.tuple();
        int idx = 1;

        if (notice != null) {
            if (notice.getNoticeTitle() != null && !notice.getNoticeTitle().isEmpty()) {
                sql.append(" AND notice_title LIKE $").append(idx++);
                params.addString("%" + notice.getNoticeTitle() + "%");
            }
            if (notice.getNoticeType() != null && !notice.getNoticeType().isEmpty()) {
                sql.append(" AND notice_type = $").append(idx++);
                params.addString(notice.getNoticeType());
            }
            if (notice.getStatus() != null && !notice.getStatus().isEmpty()) {
                sql.append(" AND status = $").append(idx++);
                params.addString(notice.getStatus());
            }
        }
        sql.append(" ORDER BY notice_id DESC LIMIT $").append(idx++).append(" OFFSET $").append(idx);
        params.addInteger(size).addInteger(offset);

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(this::toNoticeList);
    }

    /**
     * 统计公告总数
     *
     * @param notice 公告查询条件（可为 null）
     * @return 总条数
     */
    public Future<Long> searchCount(SysNotice notice) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) as cnt FROM sys_notice WHERE 1=1");
        Tuple params = Tuple.tuple();
        int idx = 1;

        if (notice != null) {
            if (notice.getNoticeTitle() != null && !notice.getNoticeTitle().isEmpty()) {
                sql.append(" AND notice_title LIKE $").append(idx++);
                params.addString("%" + notice.getNoticeTitle() + "%");
            }
            if (notice.getNoticeType() != null && !notice.getNoticeType().isEmpty()) {
                sql.append(" AND notice_type = $").append(idx++);
                params.addString(notice.getNoticeType());
            }
            if (notice.getStatus() != null && !notice.getStatus().isEmpty()) {
                sql.append(" AND status = $").append(idx++);
                params.addString(notice.getStatus());
            }
        }

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(rows -> rows.iterator().next().getLong("cnt"));
    }
}
