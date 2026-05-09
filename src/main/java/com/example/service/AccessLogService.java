package com.example.service;

import com.example.core.PageResult;
import com.example.entity.AccessLog;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.List;

/**
 * AccessLogService — 访问日志业务接口
 */
public interface AccessLogService {

    /** 按ID查询 */
    Future<AccessLog> findById(Long id);

    /** 按用户查询 */
    Future<List<AccessLog>> findByUser(Long userId, int limit);

    /** 按路径查询 */
    Future<List<AccessLog>> findByPath(String path, int limit);

    /** 动态条件搜索 + 分页 */
    Future<PageResult<AccessLog>> search(Long userId, String username, String method,
                                          String path, Integer statusCode, String userIp,
                                          String from, String to,
                                          int page, int size);

    /** 清理过期日志（读取 sys_config 保留天数后执行） */
    Future<Long> cleanupExpired();

    /** 手动清理指定天数之前的日志 */
    Future<Long> cleanupOlderThan(int retentionDays);

    /** 获取概览统计 */
    Future<JsonObject> getStats(int days);

    /** 获取 Top 路径 */
    Future<List<JsonObject>> getTopPaths(int days, int limit);

    /** 获取状态码统计 */
    Future<List<JsonObject>> getStatusCodeStats(int days);

    /** 获取保留天数配置 */
    Future<Integer> getRetentionDays();
}
