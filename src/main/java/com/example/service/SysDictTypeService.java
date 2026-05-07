package com.example.service;

import com.example.core.PageResult;
import com.example.entity.SysDictType;
import io.vertx.core.Future;

import java.util.List;

/**
 * System Dictionary Type Service Interface
 */
public interface SysDictTypeService {

    Future<List<SysDictType>> findAll();
    Future<SysDictType> findById(Long id);
    Future<SysDictType> findByDictType(String dictType);
    Future<SysDictType> create(SysDictType dictType);
    Future<SysDictType> update(Long id, SysDictType dictType);
    Future<Void> delete(Long id);
    Future<Boolean> existsByDictType(String dictType);
    Future<PageResult<SysDictType>> findPaginated(int page, int size);
    Future<PageResult<SysDictType>> searchPaginated(String dictName, String dictType, String status, int page, int size);
    Future<Long> count();
}
