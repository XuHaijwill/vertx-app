package com.example.service.impl;

import com.example.core.PageResult;
import com.example.entity.AccessLog;
import com.example.repository.AccessLogRepository;
import com.example.service.AccessLogService;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * AccessLogServiceImpl — 访问日志业务实现
 */
public class AccessLogServiceImpl extends BaseServiceImpl<AccessLogRepository>
    implements AccessLogService {

    private static final Logger LOG = LoggerFactory.getLogger(AccessLogServiceImpl.class);

    public AccessLogServiceImpl(Vertx vertx) {
        super(vertx, AccessLogRepository::new);
    }

    @Override
    public Future<AccessLog> findById(Long id) {
        if (!dbAvailable) return failIfUnavailableNull();
        return repo.findById(id);
    }

    @Override
    public Future<List<AccessLog>> findByUser(Long userId, int limit) {
        if (!dbAvailable) return failIfUnavailable();
        return repo.findByUser(userId, limit);
    }

    @Override
    public Future<List<AccessLog>> findByPath(String path, int limit) {
        if (!dbAvailable) return failIfUnavailable();
        return repo.findByPath(path, limit);
    }

    @Override
    public Future<PageResult<AccessLog>> search(Long userId, String username, String method,
                                                  String path, Integer statusCode, String userIp,
                                                  String from, String to,
                                                  int page, int size) {
        if (!dbAvailable) {
            return Future.succeededFuture(new PageResult<>(List.of(), 0, page, size));
        }
        return repo.search(userId, username, method, path, statusCode, userIp, from, to, page, size)
            .compose(list -> {
                return repo.searchCount(userId, username, method, path, statusCode, userIp, from, to)
                    .map(count -> new PageResult<>(list, count, page, size));
            });
    }

    @Override
    public Future<Long> cleanupExpired() {
        if (!dbAvailable) return Future.succeededFuture(0L);
        return repo.getRetentionDays()
            .compose(retentionDays -> {
                if (retentionDays <= 0) {
                    LOG.info("[ACCESS-LOG] Auto-cleanup disabled (retentionDays=0)");
                    return Future.succeededFuture(0L);
                }
                return cleanupOlderThan(retentionDays);
            });
    }

    @Override
    public Future<Long> cleanupOlderThan(int retentionDays) {
        if (!dbAvailable) return Future.succeededFuture(0L);
        LOG.info("[ACCESS-LOG] Cleaning up logs older than {} days", retentionDays);
        return repo.deleteOlderThan(retentionDays)
            .map(count -> {
                LOG.info("[ACCESS-LOG] Cleaned up {} expired log records", count);
                return count;
            });
    }

    @Override
    public Future<JsonObject> getStats(int days) {
        if (!dbAvailable) {
            return Future.succeededFuture(new JsonObject()
                .put("total", 0).put("days", days).put("demoMode", true));
        }
        return repo.getStats(days);
    }

    @Override
    public Future<List<JsonObject>> getTopPaths(int days, int limit) {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return repo.getTopPaths(days, limit);
    }

    @Override
    public Future<List<JsonObject>> getStatusCodeStats(int days) {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return repo.getStatusCodeStats(days);
    }

    @Override
    public Future<Integer> getRetentionDays() {
        if (!dbAvailable) return Future.succeededFuture(90);
        return repo.getRetentionDays();
    }
}
