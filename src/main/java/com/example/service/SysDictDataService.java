package com.example.service;

import com.example.core.PageResult;
import com.example.entity.SysDictData;
import io.vertx.core.Future;

import java.util.List;

/**
 * System Dictionary Data Service Interface
 */
public interface SysDictDataService {

    Future<List<SysDictData>> findAll();
    Future<SysDictData> findById(Long id);
    Future<List<SysDictData>> findByDictType(String dictType);
    Future<SysDictData> create(SysDictData dictData);
    Future<SysDictData> update(Long id, SysDictData dictData);
    Future<Void> delete(Long id);
    Future<Void> deleteByDictType(String dictType);
    Future<Boolean> existsByDictTypeAndValue(String dictType, String dictValue);
    Future<PageResult<SysDictData>> findPaginated(int page, int size);
    Future<PageResult<SysDictData>> searchPaginated(String dictType, String dictLabel, String status, int page, int size);
    Future<Long> count();
}