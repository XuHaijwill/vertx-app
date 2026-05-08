package com.example.service.impl;

import com.example.entity.ScheduledTask;
import com.example.core.PageResult;
import com.example.repository.ScheduledTaskRepository;
import com.example.service.ScheduledTaskService;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.util.ArrayList;
import java.util.List;

public class ScheduledTaskServiceImpl extends BaseServiceImpl<ScheduledTaskRepository> implements ScheduledTaskService {

    public ScheduledTaskServiceImpl(Vertx vertx) {        super(vertx, ScheduledTaskRepository::new);

}
    @Override
    public Future<List<ScheduledTask>> findAll() {
        if (!dbAvailable) return failIfUnavailable();
        return repo.findAll();
    }

    @Override
    public Future<ScheduledTask> findById(Long id) {
        if (!dbAvailable) return failIfUnavailableNull();
        return repo.findById(id);
    }

    @Override
    public Future<ScheduledTask> findByName(String name) {
        if (!dbAvailable) return failIfUnavailableNull();
        return repo.findByName(name);
    }

    @Override
    public Future<List<ScheduledTask>> findByStatus(String status) {
        if (!dbAvailable) return failIfUnavailable();
        return repo.findByStatus(status);
    }

    @Override
    public Future<List<ScheduledTask>> findByTaskType(String taskType) {
        if (!dbAvailable) return failIfUnavailable();
        return repo.findByTaskType(taskType);
    }

    @Override
    public Future<List<ScheduledTask>> findPaginated(int page, int size) {
        if (!dbAvailable) return failIfUnavailable();
        return repo.findPaginated(page, size);
    }

    @Override
    public Future<List<ScheduledTask>> search(String name, String taskType, String status,
                                                String lastRunStatus, int page, int size) {
        if (!dbAvailable) return failIfUnavailable();
        return repo.search(name, taskType, status, lastRunStatus, page, size);
    }

    @Override
    public Future<Long> searchCount(String name, String taskType, String status, String lastRunStatus) {
        if (!dbAvailable) return failIfUnavailableNull();
        return repo.searchCount(name, taskType, status, lastRunStatus);
    }

    @Override
    public Future<Long> count() {
        if (!dbAvailable) return failIfUnavailableNull();
        return repo.count();
    }

    @Override
    public Future<Long> create(ScheduledTask task) {
        if (!dbAvailable) {
            task.setId(System.currentTimeMillis());
            return Future.succeededFuture(task.getId());
        }
        return repo.create(task);
    }

    @Override
    public Future<Void> update(ScheduledTask task) {
        if (!dbAvailable) return Future.succeededFuture();
        return repo.update(task);
    }

    @Override
    public Future<Void> delete(Long id) {
        if (!dbAvailable) return Future.succeededFuture();
        return repo.delete(id);
    }

    @Override
    public Future<Boolean> existsByName(String name) {
        if (!dbAvailable) return failIfUnavailableNull();
        return repo.existsByName(name).map(c -> c > 0);
    }

    @Override
    public Future<Boolean> existsByNameAndNotId(String name, Long excludeId) {
        if (!dbAvailable) return failIfUnavailableNull();
        return repo.existsByNameAndNotId(name, excludeId);
    }

    // ================================================================
    // Helpers
    // ================================================================

    /** Convert List<ScheduledTask> → List<JsonObject> for API responses */
    public List<io.vertx.core.json.JsonObject> toJsonList(List<ScheduledTask> list) {
        List<io.vertx.core.json.JsonObject> result = new ArrayList<>();
        if (list == null) return result;
        for (ScheduledTask task : list) result.add(task.toJson());
        return result;
    }
}
