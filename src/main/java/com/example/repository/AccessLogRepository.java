package com.example.repository;

import com.example.db.DatabaseVerticle;
import com.example.entity.AccessLog;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.util.List;

/**
 * AccessLogRepository — 访问日志数据库操作
 *
 * <p>支持：
 * <ul>
 *   <li>异步写入（独立连接，不阻塞业务请求）</li>
 *   <li>多条件动态查询 + 分页</li>
 *   <li>按保留天数清理过期记录</li>
 *   <li>统计查询（按路径、用户、状态码）</li>
 * </ul>
 */
public class AccessLogRepository {

    private final Vertx vertx;

    public AccessLogRepository(Vertx vertx) {
        this.vertx = vertx;
    }

    // ================================================================
    // 写入（异步，独立连接）
    // ================================================================

    /**
     * 异步写入访问日志（不阻塞业务请求）
     *
     * @param log 访问日志实体
     * @return Future<Long> 新记录 ID
     */
    public Future<Long> insert(AccessLog log) {
        String sql = """
            INSERT INTO access_log
              (trace_id, user_id, username, method, path, query_string,
               status_code, response_time, user_ip, user_agent, request_id,
               error_message, extra)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13)
            RETURNING id
            """;
        Tuple params = Tuple.tuple()
            .addString(log.getTraceId())
            .addLong(log.getUserId())
            .addString(log.getUsername())
            .addString(log.getMethod())
            .addString(log.getPath())
            .addString(log.getQueryString())
            .addInteger(log.getStatusCode())
            .addInteger(log.getResponseTime())
            .addString(log.getUserIp())
            .addString(log.getUserAgent())
            .addString(log.getRequestId())
            .addString(log.getErrorMessage())
            .addValue(log.getExtra());     // JSONB

        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getLong("id"))
            .onFailure(err ->
                org.slf4j.LoggerFactory.getLogger(AccessLogRepository.class)
                    .warn("[ACCESS-LOG] Failed to insert access log: {}", err.getMessage())
            );
    }

    // ================================================================
    // 查询
    // ================================================================

    /**
     * 按 ID 查询单条记录
     */
    public Future<AccessLog> findById(Long id) {
        String sql = "SELECT * FROM access_log WHERE id = $1";
        return DatabaseVerticle.query(vertx, sql, Tuple.of(id))
            .map(AccessLog::toOne);
    }

    /**
     * 按用户查询
     */
    public Future<List<AccessLog>> findByUser(Long userId, int limit) {
        String sql = "SELECT * FROM access_log WHERE user_id = $1 ORDER BY created_at DESC LIMIT $2";
        return DatabaseVerticle.query(vertx, sql, Tuple.of(userId, limit))
            .map(AccessLog::toList);
    }

    /**
     * 按路径查询
     */
    public Future<List<AccessLog>> findByPath(String path, int limit) {
        String sql = "SELECT * FROM access_log WHERE path = $1 ORDER BY created_at DESC LIMIT $2";
        return DatabaseVerticle.query(vertx, sql, Tuple.of(path, limit))
            .map(AccessLog::toList);
    }

    // ================================================================
    // 动态条件搜索 + 分页
    // ================================================================

    /**
     * 动态条件搜索（所有参数可选，AND 逻辑）
     *
     * @param userId      用户 ID（null = 任何）
     * @param username    用户名模糊匹配（null = 任何）
     * @param method      HTTP 方法（null = 任何）
     * @param path        路径模糊匹配（null = 任何）
     * @param statusCode  HTTP 状态码（null = 任何）
     * @param userIp      客户端 IP 模糊匹配（null = 任何）
     * @param from        起始时间 ISO 字符串（null = 无下界）
     * @param to          结束时间 ISO 字符串（null = 无上界）
     * @param page        页码（从 1 开始）
     * @param size        每页条数
     */
    public Future<List<AccessLog>> search(Long userId, String username, String method,
                                           String path, Integer statusCode, String userIp,
                                           String from, String to,
                                           int page, int size) {
        StringBuilder sql = new StringBuilder("SELECT * FROM access_log WHERE 1=1");
        Tuple params = Tuple.tuple();
        int idx = 1;

        if (userId != null) {
            sql.append(" AND user_id = $").append(idx++);
            params.addLong(userId);
        }
        if (username != null && !username.isBlank()) {
            sql.append(" AND username ILIKE $").append(idx++);
            params.addString("%" + username + "%");
        }
        if (method != null && !method.isBlank()) {
            sql.append(" AND method = $").append(idx++);
            params.addString(method.toUpperCase());
        }
        if (path != null && !path.isBlank()) {
            sql.append(" AND path ILIKE $").append(idx++);
            params.addString("%" + path + "%");
        }
        if (statusCode != null) {
            sql.append(" AND status_code = $").append(idx++);
            params.addInteger(statusCode);
        }
        if (userIp != null && !userIp.isBlank()) {
            sql.append(" AND user_ip ILIKE $").append(idx++);
            params.addString("%" + userIp + "%");
        }
        if (from != null && !from.isBlank()) {
            sql.append(" AND created_at >= $").append(idx++);
            params.addString(from + "T00:00:00Z");
        }
        if (to != null && !to.isBlank()) {
            sql.append(" AND created_at <= $").append(idx++);
            params.addString(to + "T23:59:59Z");
        }

        sql.append(" ORDER BY created_at DESC");
        int offset = (page - 1) * size;
        sql.append(" LIMIT $").append(idx++).append(" OFFSET $").append(idx);
        params.addInteger(size).addInteger(offset);

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(AccessLog::toList);
    }

    /**
     * 动态条件计数（与 search 参数一致）
     */
    public Future<Long> searchCount(Long userId, String username, String method,
                                      String path, Integer statusCode, String userIp,
                                      String from, String to) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS count FROM access_log WHERE 1=1");
        Tuple params = Tuple.tuple();
        int idx = 1;

        if (userId != null) {
            sql.append(" AND user_id = $").append(idx++);
            params.addLong(userId);
        }
        if (username != null && !username.isBlank()) {
            sql.append(" AND username ILIKE $").append(idx++);
            params.addString("%" + username + "%");
        }
        if (method != null && !method.isBlank()) {
            sql.append(" AND method = $").append(idx++);
            params.addString(method.toUpperCase());
        }
        if (path != null && !path.isBlank()) {
            sql.append(" AND path ILIKE $").append(idx++);
            params.addString("%" + path + "%");
        }
        if (statusCode != null) {
            sql.append(" AND status_code = $").append(idx++);
            params.addInteger(statusCode);
        }
        if (userIp != null && !userIp.isBlank()) {
            sql.append(" AND user_ip ILIKE $").append(idx++);
            params.addString("%" + userIp + "%");
        }
        if (from != null && !from.isBlank()) {
            sql.append(" AND created_at >= $").append(idx++);
            params.addString(from + "T00:00:00Z");
        }
        if (to != null && !to.isBlank()) {
            sql.append(" AND created_at <= $").append(idx++);
            params.addString(to + "T23:59:59Z");
        }

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(rows -> rows.iterator().next().getLong("count"));
    }

    // ================================================================
    // 清理
    // ================================================================

    /**
     * 删除指定天数之前的访问日志
     *
     * @param retentionDays 保留天数
     * @return Future<Long> 删除的记录数
     */
    public Future<Long> deleteOlderThan(int retentionDays) {
        String sql = """
            DELETE FROM access_log
            WHERE created_at < CURRENT_TIMESTAMP - ($1 || ' days')::INTERVAL
            """;
        return DatabaseVerticle.query(vertx, sql, Tuple.of(retentionDays))
            .map(rows -> (long) rows.rowCount());
    }

    // ================================================================
    // 统计
    // ================================================================

    /**
     * 获取访问日志概览统计
     *
     * @param days 统计最近 N 天
     */
    public Future<JsonObject> getStats(int days) {
        String sql = """
            SELECT
                COUNT(*) AS total,
                COUNT(*) FILTER (WHERE status_code >= 200 AND status_code < 300) AS success_count,
                COUNT(*) FILTER (WHERE status_code >= 400) AS error_count,
                COUNT(*) FILTER (WHERE status_code >= 500) AS server_error_count,
                AVG(response_time)::INTEGER AS avg_response_time,
                MAX(response_time) AS max_response_time,
                COUNT(DISTINCT user_id) AS unique_users,
                COUNT(DISTINCT path) AS unique_paths
            FROM access_log
            WHERE created_at >= CURRENT_TIMESTAMP - ($1 || ' days')::INTERVAL
            """;
        return DatabaseVerticle.query(vertx, sql, Tuple.of(days))
            .map(rows -> {
                var row = rows.iterator().next();
                return new JsonObject()
                    .put("total", row.getLong("total"))
                    .put("successCount", row.getLong("success_count"))
                    .put("errorCount", row.getLong("error_count"))
                    .put("serverErrorCount", row.getLong("server_error_count"))
                    .put("avgResponseTime", row.getInteger("avg_response_time"))
                    .put("maxResponseTime", row.getInteger("max_response_time"))
                    .put("uniqueUsers", row.getLong("unique_users"))
                    .put("uniquePaths", row.getLong("unique_paths"))
                    .put("days", days);
            });
    }

    /**
     * 按路径统计访问量 Top N
     */
    public Future<List<JsonObject>> getTopPaths(int days, int limit) {
        String sql = """
            SELECT path, COUNT(*) AS count, AVG(response_time)::INTEGER AS avg_response_time
            FROM access_log
            WHERE created_at >= CURRENT_TIMESTAMP - ($1 || ' days')::INTERVAL
            GROUP BY path
            ORDER BY count DESC
            LIMIT $2
            """;
        return DatabaseVerticle.query(vertx, sql, Tuple.of(days, limit))
            .map(rows -> {
                List<JsonObject> list = new java.util.ArrayList<>();
                for (Row row : rows) {
                    list.add(new JsonObject()
                        .put("path", row.getString("path"))
                        .put("count", row.getLong("count"))
                        .put("avgResponseTime", row.getInteger("avg_response_time")));
                }
                return list;
            });
    }

    /**
     * 按状态码统计
     */
    public Future<List<JsonObject>> getStatusCodeStats(int days) {
        String sql = """
            SELECT status_code, COUNT(*) AS count
            FROM access_log
            WHERE created_at >= CURRENT_TIMESTAMP - ($1 || ' days')::INTERVAL
            GROUP BY status_code
            ORDER BY count DESC
            """;
        return DatabaseVerticle.query(vertx, sql, Tuple.of(days))
            .map(rows -> {
                List<JsonObject> list = new java.util.ArrayList<>();
                for (Row row : rows) {
                    list.add(new JsonObject()
                        .put("statusCode", row.getInteger("status_code"))
                        .put("count", row.getLong("count")));
                }
                return list;
            });
    }

    /**
     * 获取保留天数配置值（从 sys_config 表读取）
     */
    public Future<Integer> getRetentionDays() {
        String sql = "SELECT config_value FROM sys_config WHERE config_key = 'sys.access-log.retentionDays'";
        return DatabaseVerticle.query(vertx, sql)
            .map(rows -> {
                var it = rows.iterator();
                if (it.hasNext()) {
                    try {
                        return Integer.parseInt(it.next().getString("config_value"));
                    } catch (NumberFormatException e) {
                        return 90; // default
                    }
                }
                return 90; // default
            });
    }
}
