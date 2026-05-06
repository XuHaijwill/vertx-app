package com.example.service.impl;

import com.example.core.BusinessException;
import com.example.core.PageResult;
import com.example.db.AuditAction;
import com.example.db.AuditLogger;
import com.example.db.DatabaseVerticle;
import com.example.entity.SysDictType;
import com.example.repository.SysDictTypeRepository;
import com.example.service.SysDictTypeService;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

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

    // ================================================================
    // HELPERS
    // ================================================================

    private static List<JsonObject> toJsonObjectList(List<SysDictType> list) {
        return list.stream().map(SysDictType::toJson).collect(Collectors.toList());
    }

    /** create/update 内部执行 + 审计，返回 SysDictType */
    private Future<SysDictType> doCreate(SysDictType dictType) {
        return repo.create(dictType)
            .compose(created -> audit.log(
                    AuditAction.AUDIT_CREATE, "sys_dict_type",
                    String.valueOf(created.getDictId()),
                    null, created.toJson())
                .map(created));
    }

    private Future<SysDictType> doUpdate(Long id, SysDictType dictType, SysDictType existing) {
        return repo.update(id, dictType)
            .compose(updated -> audit.log(
                    AuditAction.AUDIT_UPDATE, "sys_dict_type",
                    String.valueOf(id),
                    existing.toJson(), updated.toJson())
                .map(updated));
    }

    // ================================================================
    // IMPLEMENTATIONS
    // ================================================================

    @Override
    public Future<List<SysDictType>> findAll() {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return repo.findAll();
    }

    @Override
    public Future<SysDictType> findById(Long id) {
        if (!dbAvailable) return Future.succeededFuture(null);
        return repo.findById(id)
            .map(dict -> {
                if (dict == null) throw BusinessException.notFound("DictType");
                return dict;
            });
    }

    @Override
    public Future<SysDictType> findByDictType(String dictType) {
        if (!dbAvailable) return Future.succeededFuture(null);
        return repo.findByDictType(dictType);
    }

    @Override
    public Future<JsonObject> create(SysDictType dictType) {
        if (dictType == null) {
            return Future.failedFuture(BusinessException.badRequest("Request body is required"));
        }
        String type = dictType.getDictType();
        if (type == null || type.isBlank()) {
            return Future.failedFuture(BusinessException.badRequest("dictType is required"));
        }

        if (!dbAvailable) {
            return Future.succeededFuture(dictType.toJson().put("dictId", System.currentTimeMillis()));
        }

        return repo.existsByDictType(type)
            .compose(exists -> {
                if (exists) {
                    return Future.<JsonObject>failedFuture(
                        BusinessException.conflict("Dict type already exists: " + type));
                }
                return doCreate(dictType).map(SysDictType::toJson);
            });
    }

    @Override
    public Future<JsonObject> update(Long id, SysDictType dictType) {
        if (!dbAvailable) {
            return Future.succeededFuture(dictType.toJson().put("dictId", id));
        }
        return repo.findById(id)
            .compose((SysDictType existing) -> {
                if (existing == null) {
                    return Future.<JsonObject>failedFuture(BusinessException.notFound("DictType"));
                }
                String newType = dictType.getDictType();
                if (newType != null && !newType.equals(existing.getDictType())) {
                    return repo.existsByDictType(newType)
                        .compose(conflict -> {
                            if (conflict) {
                                return Future.<JsonObject>failedFuture(
                                    BusinessException.conflict("Dict type already exists: " + newType));
                            }
                            return doUpdate(id, dictType, existing).map(SysDictType::toJson);
                        });
                }
                return doUpdate(id, dictType, existing).map(SysDictType::toJson);
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
                            existing.toJson(), null)
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
                .map(list -> new PageResult<>(toJsonObjectList(list), total, page, size)));
    }

    @Override
    public Future<PageResult<JsonObject>> searchPaginated(String dictName, String dictType, String status, int page, int size) {
        if (!dbAvailable) {
            return Future.succeededFuture(new PageResult<>(List.of(), 0, page, size));
        }
        return repo.searchCount(dictName, dictType, status)
            .compose(total -> repo.searchPaginated(dictName, dictType, status, page, size)
                .map(list -> new PageResult<>(toJsonObjectList(list), total, page, size)));
    }

    @Override
    public Future<Long> count() {
        if (!dbAvailable) return Future.succeededFuture(0L);
        return repo.count().map(c -> c.longValue());
    }
}
