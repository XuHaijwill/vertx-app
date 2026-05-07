package com.example.service.impl;

import com.example.core.BusinessException;
import com.example.core.PageResult;
import com.example.db.AuditAction;
import com.example.db.AuditLogger;
import com.example.db.DatabaseVerticle;
import com.example.entity.SysDictData;
import com.example.repository.SysDictDataRepository;
import com.example.service.SysDictDataService;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.util.List;

/**
 * System Dictionary Data Service Implementation
 */
public class SysDictDataServiceImpl implements SysDictDataService {

    private final SysDictDataRepository repo;
    private final AuditLogger audit;
    private final boolean dbAvailable;

    public SysDictDataServiceImpl(Vertx vertx) {
        this.repo = new SysDictDataRepository(vertx);
        this.audit = new AuditLogger(vertx);
        this.dbAvailable = DatabaseVerticle.getPool(vertx) != null;
    }

    // ================================================================
    // HELPERS
    // ================================================================

    /** create 内部执行 + 审计 */
    private Future<SysDictData> doCreate(SysDictData dictData) {
        return repo.create(dictData)
            .compose(created -> audit.log(
                    AuditAction.AUDIT_CREATE, "sys_dict_data",
                    String.valueOf(created.getDictCode()),
                    null, created.toJson())
                .map(created));
    }

    /** update 内部执行 + 审计 */
    private Future<SysDictData> doUpdate(Long id, SysDictData dictData, SysDictData existing) {
        return repo.update(id, dictData)
            .compose(updated -> audit.log(
                    AuditAction.AUDIT_UPDATE, "sys_dict_data",
                    String.valueOf(id),
                    existing.toJson(), updated.toJson())
                .map(updated));
    }

    // ================================================================
    // READ
    // ================================================================

    @Override
    public Future<List<SysDictData>> findAll() {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return repo.findAll();
    }

    @Override
    public Future<SysDictData> findById(Long id) {
        if (!dbAvailable) return Future.succeededFuture(null);
        return repo.findById(id)
            .map(dict -> {
                if (dict == null) throw BusinessException.notFound("DictData");
                return dict;
            });
    }

    @Override
    public Future<List<SysDictData>> findByDictType(String dictType) {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return repo.findByDictType(dictType);
    }

    // ================================================================
    // WRITE
    // ================================================================

    @Override
    public Future<SysDictData> create(SysDictData dictData) {
        if (dictData == null)
            throw BusinessException.badRequest("Request body is required");
        if (dictData.getDictType() == null || dictData.getDictType().isBlank())
            throw BusinessException.badRequest("dictType is required");

        if (!dbAvailable) {
            return Future.succeededFuture(
                SysDictData.fromJson(dictData.toJson().put("dictCode", System.currentTimeMillis())));
        }
        return doCreate(dictData);
    }

    @Override
    public Future<SysDictData> update(Long id, SysDictData dictData) {
        if (!dbAvailable) {
            return Future.succeededFuture(
                SysDictData.fromJson(dictData.toJson().put("dictCode", id)));
        }
        return repo.findById(id)
            .compose(existing -> {
                if (existing == null)
                    throw BusinessException.notFound("DictData");
                return doUpdate(id, dictData, existing);
            });
    }

    @Override
    public Future<Void> delete(Long id) {
        if (!dbAvailable) return Future.succeededFuture();
        return repo.findById(id)
            .compose(existing -> {
                if (existing == null)
                    throw BusinessException.notFound("DictData");
                return repo.delete(id)
                    .compose(v -> audit.log(AuditAction.AUDIT_DELETE, "sys_dict_data",
                        String.valueOf(id), existing.toJson(), null).mapEmpty());
            });
    }

    @Override
    public Future<Void> deleteByDictType(String dictType) {
        if (!dbAvailable) return Future.succeededFuture();
        return repo.deleteByDictType(dictType).mapEmpty();
    }

    // ================================================================
    // QUERY / COUNT
    // ================================================================

    @Override
    public Future<Boolean> existsByDictTypeAndValue(String dictType, String dictValue) {
        if (!dbAvailable) return Future.succeededFuture(false);
        return repo.existsByDictTypeAndValue(dictType, dictValue);
    }

    @Override
    public Future<PageResult<SysDictData>> findPaginated(int page, int size) {
        if (!dbAvailable)
            return Future.succeededFuture(new PageResult<>(List.of(), 0, page, size));
        return repo.count()
            .compose(total -> repo.findPaginated(page, size)
                .map(list -> new PageResult<>(list, total, page, size)));
    }

    @Override
    public Future<PageResult<SysDictData>> searchPaginated(
            String dictType, String dictLabel, String status, int page, int size) {
        if (!dbAvailable)
            return Future.succeededFuture(new PageResult<>(List.of(), 0, page, size));
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