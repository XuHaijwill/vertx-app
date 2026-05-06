package com.example.service;

import com.example.core.PageResult;
import com.example.entity.SysDictType;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.List;

/**
 * System Dictionary Type Service Interface
 */
public interface SysDictTypeService {

    Future<List<SysDictType>> findAll();
    Future<SysDictType> findById(Long id);
    Future<SysDictType> findByDictType(String dictType);
    Future<JsonObject> create(SysDictType dictType);
    Future<JsonObject> update(Long id, SysDictType dictType);
    Future<Void> delete(Long id);
    Future<Boolean> existsByDictType(String dictType);
    Future<PageResult<JsonObject>> findPaginated(int page, int size);
    Future<PageResult<JsonObject>> searchPaginated(String dictName, String dictType, String status, int page, int size);
    Future<Long> count();
}
