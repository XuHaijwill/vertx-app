package com.example.service.impl;

import com.example.core.BusinessException;
import com.example.core.PageResult;
import com.example.db.AuditAction;
import com.example.db.AuditLogger;
import com.example.db.DatabaseVerticle;
import com.example.repository.SysDictDataRepository;
import com.example.service.SysDictDataService;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * System Dictionary Data Service Implementation
 */
public class SysDictDataServiceImpl implements SysDictDataService {

    private static final Logger LOG = LoggerFactory.getLogger(SysDictDataServiceImpl.class);

    private final SysDictDataRepository repo;
    private final AuditLogger audit;
    private final boolean dbAvailable;

    public SysDictDataServiceImpl(Vertx vertx) {
        this.repo = new SysDictDataRepository(vertx);
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
                if (dict == null) throw BusinessException.notFound("DictData");
                return dict;
            });
    }

    @Override
    public Future<List<JsonObject>> findByDictType(String dictType) {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return repo.findByDictType(dictType);
    }

    @Override
    public Future<JsonObject> create(JsonObject dictData) {
        if (dictData == null) {
            return Future.failedFuture(BusinessException.badRequest("Request body is required"));
        }
        String dictType = dictData.getString("dictType");
        if (dictType == null || dictType.isBlank()) {
            return Future.failedFuture(BusinessException.badRequest("dictType is required"));
        }

        if (!dbAvailable) {
            return Future.succeededFuture(dictData.copy().put("dictCode", System.currentTimeMillis()));
        }

        return repo.create(dictData)
            .compose(created -> audit.log(
                    AuditAction.AUDIT_CREATE, "sys_dict_data",
                    String.valueOf(created.getLong("dictCode")),
                    null, created)
                .map(created));
    }

    @Override
    public Future<JsonObject> update(Long id, JsonObject dictData) {
        if (!dbAvailable) {
            return Future.succeededFuture(dictData.copy().put("dictCode", id));
        }
        return repo.findById(id)
            .compose(existing -> {
                if (existing == null) {
                    return Future.<JsonObject>failedFuture(BusinessException.notFound("DictData"));
                }
                return repo.update(id, dictData)
                    .compose(updated -> audit.log(
                            AuditAction.AUDIT_UPDATE, "sys_dict_data",
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
                    return Future.<Void>failedFuture(BusinessException.notFound("DictData"));
                }
                return repo.delete(id)
                    .compose(deleted -> audit.log(
                            AuditAction.AUDIT_DELETE, "sys_dict_data",
                            String.valueOf(id),
                            existing, null)
                        .mapEmpty());
            });
    }

    @Override
    public Future<Void> deleteByDictType(String dictType) {
        if (!dbAvailable) return Future.succeededFuture();
        return repo.deleteByDictType(dictType).mapEmpty();
    }

    @Override
    public Future<Boolean> existsByDictTypeAndValue(String dictType, String dictValue) {
        if (!dbAvailable) return Future.succeededFuture(false);
        return repo.existsByDictTypeAndValue(dictType, dictValue);
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
    public Future<PageResult<JsonObject>> searchPaginated(String dictType, String dictLabel, String status, int page, int size) {
        if (!dbAvailable) {
            return Future.succeededFuture(new PageResult<>(List.of(), 0, page, size));
        }
        return repo.searchCount(dictType, dictLabel, null, status)
            .compose(total -> repo.searchPaginated(dictType, dictLabel, null, status, page, size)
                .map(list -> new PageResult<>(list, total, page, size)));
    }

    @Override
    public Future<Long> count() {
        if (!dbAvailable) return Future.succeededFuture(0L);
        return repo.count().map(c -> c.longValue());
    }
}
