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
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
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

    private JsonObject toJson(SysConfig c) { return c != null ? c.toJson() : null; }

    private List<JsonObject> toJsonList(List<SysConfig> list) {
        if (list == null) return List.of();
        List<JsonObject> result = new ArrayList<>();
        for (SysConfig c : list) { result.add(c.toJson()); }
        return result;
    }

    @Override
    public Future<List<JsonObject>> findAll() {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return repo.findAll().map(this::toJsonList);
    }

    @Override
    public Future<JsonObject> findById(Long id) {
        if (!dbAvailable) return Future.succeededFuture(null);
        return repo.findById(id)
            .map(c -> {
                if (c == null) throw BusinessException.notFound("Config");
                return c.toJson();
            });
    }

    @Override
    public Future<JsonObject> findByConfigKey(String configKey) {
        if (!dbAvailable) return Future.succeededFuture(null);
        return repo.findByConfigKey(configKey).map(this::toJson);
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

    @Override
    public Future<JsonObject> create(JsonObject config) {
        if (config == null) {
            return Future.failedFuture(BusinessException.badRequest("Request body is required"));
        }
        String configKey = config.getString("configKey");
        if (configKey == null || configKey.isBlank()) {
            return Future.failedFuture(BusinessException.badRequest("configKey is required"));
        }

        if (!dbAvailable) {
            return Future.succeededFuture(config.copy().put("configId", System.currentTimeMillis()));
        }

        SysConfig entity = SysConfig.fromJson(config);

        return repo.existsByConfigKey(configKey)
            .compose(exists -> {
                if (exists) {
                    return Future.<JsonObject>failedFuture(
                        BusinessException.conflict("Config key already exists: " + configKey));
                }
                return repo.create(entity)
                    .compose(created -> audit.log(
                            AuditAction.AUDIT_CREATE, "sys_config",
                            String.valueOf(created.getConfigId()),
                            null, created.toJson())
                        .map(created.toJson()));
            });
    }

    @Override
    public Future<JsonObject> update(Long id, JsonObject config) {
        if (!dbAvailable) {
            return Future.succeededFuture(config.copy().put("configId", id));
        }
        SysConfig entity = SysConfig.fromJson(config);

        return repo.findById(id)
            .compose(existing -> {
                if (existing == null) {
                    return Future.<JsonObject>failedFuture(BusinessException.notFound("Config"));
                }
                String newKey = entity.getConfigKey();
                if (newKey != null && !newKey.equals(existing.getConfigKey())) {
                    return repo.existsByConfigKey(newKey)
                        .compose(conflict -> {
                            if (conflict) {
                                return Future.<JsonObject>failedFuture(
                                    BusinessException.conflict("Config key already exists: " + newKey));
                            }
                            return repo.update(id, entity)
                                .compose(updated -> audit.log(
                                        AuditAction.AUDIT_UPDATE, "sys_config",
                                        String.valueOf(id),
                                        existing.toJson(), updated.toJson())
                                    .map(updated.toJson()));
                        });
                }
                return repo.update(id, entity)
                    .compose(updated -> audit.log(
                            AuditAction.AUDIT_UPDATE, "sys_config",
                            String.valueOf(id),
                            existing.toJson(), updated.toJson())
                        .map(updated.toJson()));
            });
    }

    @Override
    public Future<Void> delete(Long id) {
        if (!dbAvailable) return Future.succeededFuture();
        return repo.findById(id)
            .compose(existing -> {
                if (existing == null) {
                    return Future.<Void>failedFuture(BusinessException.notFound("Config"));
                }
                return repo.delete(id)
                    .compose(v -> audit.log(
                            AuditAction.AUDIT_DELETE, "sys_config",
                            String.valueOf(id),
                            existing.toJson(), null)
                        .mapEmpty());
            });
    }

    @Override
    public Future<Boolean> existsByConfigKey(String configKey) {
        if (!dbAvailable) return Future.succeededFuture(false);
        return repo.existsByConfigKey(configKey);
    }

    @Override
    public Future<PageResult<JsonObject>> findPaginated(int page, int size) {
        if (!dbAvailable) {
            return Future.succeededFuture(new PageResult<>(List.of(), 0, page, size));
        }
        return repo.count()
            .compose(total -> repo.findPaginated(page, size)
                .map(list -> new PageResult<>(toJsonList(list), total, page, size)));
    }

    @Override
    public Future<PageResult<JsonObject>> searchPaginated(String configName, String configKey, String group, int page, int size) {
        if (!dbAvailable) {
            return Future.succeededFuture(new PageResult<>(List.of(), 0, page, size));
        }
        return repo.searchCount(configName, configKey, null, group)
            .compose(total -> repo.searchPaginated(configName, configKey, null, group, page, size)
                .map(list -> new PageResult<>(toJsonList(list), total, page, size)));
    }

    @Override
    public Future<Long> count() {
        if (!dbAvailable) return Future.succeededFuture(0L);
        return repo.count().map(c -> c.longValue());
    }
}
