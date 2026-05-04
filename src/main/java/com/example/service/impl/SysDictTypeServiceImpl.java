package com.example.service.impl;

import com.example.core.BusinessException;
import com.example.core.PageResult;
import com.example.db.AuditAction;
import com.example.db.AuditLogger;
import com.example.db.DatabaseVerticle;
import com.example.repository.SysDictTypeRepository;
import com.example.service.SysDictTypeService;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * System Dictionary Type Service Implementation
 */
public class SysDictTypeServiceImpl implements SysDictTypeService {

    private static final Logger LOG = LoggerFactory.getLogger(SysDictTypeServiceImpl.class);

    private final SysDictTypeRepository repo;
    private final AuditLogger audit;
    private final boolean dbAvailable;

    public SysDictTypeServiceImpl(Vertx vertx) {
        this.repo = new SysDictTypeRepository(vertx);
        this.audit = new AuditLogger(vertx);
        this.dbAvailable = DatabaseVerticle.getPool(vertx) != null;
    }

    @Override
    public Future<List<JsonObject>> findAll() {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return repo.findAll();
    }

    @Override
    public Future<JsonObject> findById(Long id) {
        if (!dbAvailable) return Future.succeededFuture(null);
        return repo.findById(id)
            .map(dict -> {
                if (dict == null) throw BusinessException.notFound("DictType");
                return dict;
            });
    }

    @Override
    public Future<JsonObject> findByDictType(String dictType) {
        if (!dbAvailable) return Future.succeededFuture(null);
        return repo.findByDictType(dictType);
    }

    @Override
    public Future<JsonObject> create(JsonObject dictType) {
        if (dictType == null) {
            return Future.failedFuture(BusinessException.badRequest("Request body is required"));
        }
        String type = dictType.getString("dictType");
        if (type == null || type.isBlank()) {
            return Future.failedFuture(BusinessException.badRequest("dictType is required"));
        }

        if (!dbAvailable) {
            return Future.succeededFuture(dictType.copy().put("dictId", System.currentTimeMillis()));
        }

        return repo.existsByDictType(type)
            .compose(exists -> {
                if (exists) {
                    return Future.<JsonObject>failedFuture(
                        BusinessException.conflict("Dict type already exists: " + type));
                }
                return repo.create(dictType)
                    .compose(created -> audit.log(
                            AuditAction.AUDIT_CREATE, "sys_dict_type",
                            String.valueOf(created.getLong("dictId")),
                            null, created)
                        .map(created));
            });
    }

    @Override
    public Future<JsonObject> update(Long id, JsonObject dictType) {
        if (!dbAvailable) {
            return Future.succeededFuture(dictType.copy().put("dictId", id));
        }
        return repo.findById(id)
            .compose(existing -> {
                if (existing == null) {
                    return Future.<JsonObject>failedFuture(BusinessException.notFound("DictType"));
                }
                String newType = dictType.getString("dictType");
                if (newType != null && !newType.equals(existing.getString("dict_type"))) {
                    return repo.existsByDictType(newType)
                        .compose(conflict -> {
                            if (conflict) {
                                return Future.<JsonObject>failedFuture(
                                    BusinessException.conflict("Dict type already exists: " + newType));
                            }
                            return repo.update(id, dictType)
                                .compose(updated -> audit.log(
                                        AuditAction.AUDIT_UPDATE, "sys_dict_type",
                                        String.valueOf(id),
                                        existing, updated)
                                    .map(updated));
                        });
                }
                return repo.update(id, dictType)
                    .compose(updated -> audit.log(
                            AuditAction.AUDIT_UPDATE, "sys_dict_type",
                            String.valueOf(id),
                            existing, updated)
                        .map(updated));
            });
    }

    @Override
    public Future<Void> delete(Long id) {
        if (!dbAvailable) return Future.succeededFuture();
        return repo.findById(id)
            .compose(existing -> {
                if (existing == null) {
                    return Future.<Void>failedFuture(BusinessException.notFound("DictType"));
                }
                return repo.delete(id)
                    .compose(v -> audit.log(
                            AuditAction.AUDIT_DELETE, "sys_dict_type",
                            String.valueOf(id),
                            existing, null)
                        .mapEmpty());
            });
    }

    @Override
    public Future<Boolean> existsByDictType(String dictType) {
        if (!dbAvailable) return Future.succeededFuture(false);
        return repo.existsByDictType(dictType);
    }

    @Override
    public Future<PageResult<JsonObject>> findPaginated(int page, int size) {
        if (!dbAvailable) {
            return Future.succeededFuture(new PageResult<>(List.of(), 0, page, size));
        }
        return repo.count()
            .compose(total -> repo.findPaginated(page, size)
                .map(list -> new PageResult<>(list, total, page, size)));
    }

    @Override
    public Future<PageResult<JsonObject>> searchPaginated(String dictName, String dictType, String status, int page, int size) {
        if (!dbAvailable) {
            return Future.succeededFuture(new PageResult<>(List.of(), 0, page, size));
        }
        return repo.searchCount(dictName, dictType, status)
            .compose(total -> repo.searchPaginated(dictName, dictType, status, page, size)
                .map(list -> new PageResult<>(list, total, page, size)));
    }

    @Override
    public Future<Long> count() {
        if (!dbAvailable) return Future.succeededFuture(0L);
        return repo.count().map(c -> c.longValue());
    }
}
