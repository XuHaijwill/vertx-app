package com.example.service;

import com.example.core.PageResult;
import com.example.entity.SysConfig;
import io.vertx.core.Future;

import java.util.List;

/**
 * System Config Service Interface
 */
public interface SysConfigService {

    Future<List<SysConfig>> findAll();
    Future<SysConfig> findById(Long id);
    Future<SysConfig> findByConfigKey(String configKey);
    Future<String> getConfigValue(String configKey);
    Future<String> getConfigValue(String configKey, String defaultValue);
    Future<SysConfig> create(SysConfig config);
    Future<SysConfig> update(Long id, SysConfig config);
    Future<Void> delete(Long id);
    Future<Boolean> existsByConfigKey(String configKey);
    Future<PageResult<SysConfig>> findPaginated(int page, int size);
    Future<PageResult<SysConfig>> searchPaginated(String configName, String configKey, String group, int page, int size);
    Future<Long> count();
}