package com.example.service;

import com.example.entity.ScheduledTask;
import io.vertx.core.Future;

import java.util.List;

public interface ScheduledTaskService {

    Future<List<ScheduledTask>> findAll();
    Future<ScheduledTask> findById(Long id);
    Future<ScheduledTask> findByName(String name);
    Future<List<ScheduledTask>> findByStatus(String status);
    Future<List<ScheduledTask>> findByTaskType(String taskType);
    Future<List<ScheduledTask>> findPaginated(int page, int size);
    Future<List<ScheduledTask>> search(String name, String taskType, String status, String lastRunStatus, int page, int size);
    Future<Long> searchCount(String name, String taskType, String status, String lastRunStatus);
    Future<Long> count();

    Future<Long> create(ScheduledTask task);
    Future<Void> update(ScheduledTask task);
    Future<Void> delete(Long id);
    Future<Boolean> existsByName(String name);
    Future<Boolean> existsByNameAndNotId(String name, Long excludeId);
}
