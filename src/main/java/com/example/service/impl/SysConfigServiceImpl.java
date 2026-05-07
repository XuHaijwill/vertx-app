package com.example.service.impl;

import com.example.core.BusinessException;
import com.example.core.PageResult;
import com.example.db.AuditAction;
import com.example.db.AuditLogger;
import com.example.db.DatabaseVerticle;
import com.example.entity.SysConfig;
import com.example.repository.SysConfigRepository;
import com.example.service.SysConfigService;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.util.List;

/**
 * System Config Service Implementation
 */
public class SysConfigServiceImpl implements SysConfigService {

    private final SysConfigRepository repo;
    private final AuditLogger audit;
    private final boolean dbAvailable;

    public SysConfigServiceImpl(Vertx vertx) {
        this.repo = new SysConfigRepository(vertx);
        this.audit = new AuditLogger(vertx);
        this.dbAvailable = DatabaseVerticle.getPool(vertx) != null;
    }

    // ================================================================
    // READ
    // ================================================================

    @Override
    public Future<List<SysConfig>> findAll() {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return repo.findAll();
    }

    @Override
    public Future<SysConfig> findById(Long id) {
        if (!dbAvailable) return Future.succeededFuture(null);
        return repo.findById(id)
            .map(c -> {
                if (c == null) throw BusinessException.notFound("Config");
                return c;
            });
    }

    @Override
    public Future<SysConfig> findByConfigKey(String configKey) {
        if (!dbAvailable) return Future.succeededFuture(null);
        return repo.findByConfigKey(configKey);
    }

    @Override
    public Future<String> getConfigValue(String configKey) {
        if (!dbAvailable) return Future.succeededFuture(null);
        return repo.findByConfigKey(configKey)
            .map(c -> c != null ? c.getConfigValue() : null);
    }

    @Override
    public Future<String> getConfigValue(String configKey, String defaultValue) {
        return getConfigValue(configKey).map(v -> v != null ? v : defaultValue);
    }

    // ================================================================
    // WRITE
    // ================================================================

    @Override
    public Future<SysConfig> create(SysConfig entity) {
        if (entity == null)
            throw BusinessException.badRequest("Request body is required");
        if (entity.getConfigKey() == null || entity.getConfigKey().isBlank())
            throw BusinessException.badRequest("configKey is required");

        if (!dbAvailable) {
            return Future.succeededFuture(
                SysConfig.fromJson(entity.toJson().put("configId", System.currentTimeMillis())));
        }

        return repo.existsByConfigKey(entity.getConfigKey())
            .compose(exists -> {
                if (exists)
                    throw BusinessException.conflict("Config key already exists: " + entity.getConfigKey());
                return repo.create(entity);
            })
            .compose(created ->
                audit.log(AuditAction.AUDIT_CREATE, "sys_config",
                    String.valueOf(created.getConfigId()), null, created.toJson())
                    .map(created));
    }

    @Override
    public Future<SysConfig> update(Long id, SysConfig entity) {
        if (!dbAvailable) {
            return Future.succeededFuture(
                SysConfig.fromJson(entity.toJson().put("configId", id)));
        }
        return repo.findById(id)
            .compose(existing -> {
                if (existing == null)
                    throw BusinessException.notFound("Config");
                String newKey = entity.getConfigKey();
                if (newKey != null && !newKey.equals(existing.getConfigKey())) {
                    return repo.existsByConfigKey(newKey)
                        .compose(conflict -> {
                            if (conflict)
                                throw BusinessException.conflict("Config key already exists: " + newKey);
                            return doUpdate(id, entity, existing);
                        });
                }
                return doUpdate(id, entity, existing);
            });
    }

    private Future<SysConfig> doUpdate(Long id, SysConfig entity, SysConfig existing) {
        return repo.update(id, entity)
            .compose(updated ->
                audit.log(AuditAction.AUDIT_UPDATE, "sys_config",
                    String.valueOf(id), existing.toJson(), updated.toJson())
                    .map(updated));
    }

    @Override
    public Future<Void> delete(Long id) {
        if (!dbAvailable) return Future.succeededFuture();
        return repo.findById(id)
            .compose(existing -> {
                if (existing == null)
                    throw BusinessException.notFound("Config");
                return repo.delete(id)
                    .compose(v -> audit.log(AuditAction.AUDIT_DELETE, "sys_config",
                        String.valueOf(id), existing.toJson(), null).mapEmpty());
            });
    }

    // ================================================================
    // QUERY / COUNT
    // ================================================================

    @Override
    public Future<Boolean> existsByConfigKey(String configKey) {
        if (!dbAvailable) return Future.succeededFuture(false);
        return repo.existsByConfigKey(configKey);
    }

    @Override
    public Future<PageResult<SysConfig>> findPaginated(int page, int size) {
        if (!dbAvailable)
            return Future.succeededFuture(new PageResult<>(List.of(), 0, page, size));
        return repo.count()
            .compose(total -> repo.findPaginated(page, size)
                .map(list -> new PageResult<>(list, total, page, size)));
    }

    @Override
    public Future<PageResult<SysConfig>> searchPaginated(
            String configName, String configKey, String group, int page, int size) {
        if (!dbAvailable)
            return Future.succeededFuture(new PageResult<>(List.of(), 0, page, size));
        return repo.searchCount(configName, configKey, null, group)
            .compose(total -> repo.searchPaginated(configName, configKey, null, group, page, size)
                .map(list -> new PageResult<>(list, total, page, size)));
    }

    @Override
    public Future<Long> count() {
        if (!dbAvailable) return Future.succeededFuture(0L);
        return repo.count().map(c -> c.longValue());
    }
}