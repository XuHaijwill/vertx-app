package com.example.service;

import com.example.core.PageResult;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.List;

/**
 * System Dictionary Data Service Interface
 */
public interface SysDictDataService {

    Future<List<JsonObject>> findAll();
    Future<JsonObject> findById(Long id);
    Future<List<JsonObject>> findByDictType(String dictType);
    Future<JsonObject> create(JsonObject dictData);
    Future<JsonObject> update(Long id, JsonObject dictData);
    Future<Void> delete(Long id);
    Future<Void> deleteByDictType(String dictType);
    Future<Boolean> existsByDictTypeAndValue(String dictType, String dictValue);
    Future<PageResult<JsonObject>> findPaginated(int page, int size);
    Future<PageResult<JsonObject>> searchPaginated(String dictType, String dictLabel, String status, int page, int size);
    Future<Long> count();
}
