package com.example.service;

import com.example.core.PageResult;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.List;

/**
 * System Config Service Interface
 */
public interface SysConfigService {

    Future<List<JsonObject>> findAll();
    Future<JsonObject> findById(Long id);
    Future<JsonObject> findByConfigKey(String configKey);
    Future<String> getConfigValue(String configKey);
    Future<String> getConfigValue(String configKey, String defaultValue);
    Future<JsonObject> create(JsonObject config);
    Future<JsonObject> update(Long id, JsonObject config);
    Future<Void> delete(Long id);
    Future<Boolean> existsByConfigKey(String configKey);
    Future<PageResult<JsonObject>> findPaginated(int page, int size);
    Future<PageResult<JsonObject>> searchPaginated(String configName, String configKey, String group, int page, int size);
    Future<Long> count();
}
