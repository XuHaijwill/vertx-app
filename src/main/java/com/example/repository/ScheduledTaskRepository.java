package com.example.repository;

import com.example.db.DatabaseVerticle;
import com.example.entity.ScheduledTask;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;

import java.util.List;

public class ScheduledTaskRepository {

    private final Vertx vertx;

    public ScheduledTaskRepository(Vertx vertx) {
        this.vertx = vertx;
    }

    // ================================================================
    // Read
    // ================================================================

    public Future<List<ScheduledTask>> findAll() {
        String sql = "SELECT * FROM scheduled_tasks ORDER BY id";
        return DatabaseVerticle.query(vertx, sql).map(ScheduledTask::toList);
    }

    public Future<ScheduledTask> findById(Long id) {
        String sql = "SELECT * FROM scheduled_tasks WHERE id = $1";
        return DatabaseVerticle.query(vertx, sql, Tuple.tuple().addLong(id)).map(ScheduledTask::toOne);
    }

    public Future<List<ScheduledTask>> findByStatus(String status) {
        String sql = "SELECT * FROM scheduled_tasks WHERE status = $1 ORDER BY id";
        return DatabaseVerticle.query(vertx, sql, Tuple.tuple().addString(status)).map(ScheduledTask::toList);
    }

    public Future<List<ScheduledTask>> findByTaskType(String taskType) {
        String sql = "SELECT * FROM scheduled_tasks WHERE task_type = $1 ORDER BY id";
        return DatabaseVerticle.query(vertx, sql, Tuple.tuple().addString(taskType)).map(ScheduledTask::toList);
    }

    public Future<ScheduledTask> findByName(String name) {
        String sql = "SELECT * FROM scheduled_tasks WHERE name = $1";
        return DatabaseVerticle.query(vertx, sql, Tuple.tuple().addString(name)).map(ScheduledTask::toOne);
    }

    // ================================================================
    // Search with filters
    // ================================================================

    public Future<List<ScheduledTask>> search(String name, String taskType, String status,
                                               String lastRunStatus, int page, int size) {
        StringBuilder sql = new StringBuilder("SELECT * FROM scheduled_tasks WHERE 1=1");
        Tuple params = Tuple.tuple();
        int idx = 1;

        if (name != null && !name.isBlank()) {
            sql.append(" AND name ILIKE $").append(idx++);
            params.addString("%" + name + "%");
        }
        if (taskType != null && !taskType.isBlank()) {
            sql.append(" AND task_type = $").append(idx++);
            params.addString(taskType);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = $").append(idx++);
            params.addString(status);
        }
        if (lastRunStatus != null && !lastRunStatus.isBlank()) {
            sql.append(" AND last_run_status = $").append(idx++);
            params.addString(lastRunStatus);
        }

        sql.append(" ORDER BY id");
        int offset = (page - 1) * size;
        sql.append(" LIMIT $").append(idx++).append(" OFFSET $").append(idx);
        params.addInteger(size).addInteger(offset);

        return DatabaseVerticle.query(vertx, sql.toString(), params).map(ScheduledTask::toList);
    }

    public Future<Long> searchCount(String name, String taskType, String status, String lastRunStatus) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) as count FROM scheduled_tasks WHERE 1=1");
        Tuple params = Tuple.tuple();
        int idx = 1;

        if (name != null && !name.isBlank()) {
            sql.append(" AND name ILIKE $").append(idx++);
            params.addString("%" + name + "%");
        }
        if (taskType != null && !taskType.isBlank()) {
            sql.append(" AND task_type = $").append(idx++);
            params.addString(taskType);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = $").append(idx++);
            params.addString(status);
        }
        if (lastRunStatus != null && !lastRunStatus.isBlank()) {
            sql.append(" AND last_run_status = $").append(idx++);
            params.addString(lastRunStatus);
        }

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(rows -> rows.iterator().next().getLong("count"));
    }

    public Future<List<ScheduledTask>> findPaginated(int page, int size) {
        return search(null, null, null, null, page, size);
    }

    public Future<Long> count() {
        return DatabaseVerticle.query(vertx, "SELECT COUNT(*) as count FROM scheduled_tasks")
            .map(rows -> rows.iterator().next().getLong("count"));
    }

    // ================================================================
    // Write
    // ================================================================

    public Future<Long> create(ScheduledTask task) {
        String sql = """
            INSERT INTO scheduled_tasks (name, description, task_type, config, cron,
                next_run_time, status, created_at, updated_at)
            VALUES ($1, $2, $3, $4, $5, $6, $7, NOW(), NOW())
            RETURNING id
            """;
        Tuple params = Tuple.tuple()
            .addString(task.getName())
            .addString(task.getDescription())
            .addString(task.getTaskType())
            .addValue(task.getConfig() != null ? task.getConfig() : new JsonObject())
            .addString(task.getCron())
            .addValue(task.getNextRunTime())
            .addString(task.getStatus() != null ? task.getStatus() : "ACTIVE");
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getLong("id"));
    }

    public Future<Void> update(ScheduledTask task) {
        String sql = """
            UPDATE scheduled_tasks SET
                name           = $1,
                description    = $2,
                task_type      = $3,
                config         = $4,
                cron           = $5,
                next_run_time  = $6,
                status         = $7,
                updated_at     = NOW()
            WHERE id = $8
            """;
        Tuple params = Tuple.tuple()
            .addString(task.getName())
            .addString(task.getDescription())
            .addString(task.getTaskType())
            .addValue(task.getConfig() != null ? task.getConfig() : new JsonObject())
            .addString(task.getCron())
            .addValue(task.getNextRunTime())
            .addString(task.getStatus())
            .addLong(task.getId());
        return DatabaseVerticle.query(vertx, sql, params).mapEmpty();
    }

    public Future<Void> delete(Long id) {
        String sql = "DELETE FROM scheduled_tasks WHERE id = $1";
        return DatabaseVerticle.query(vertx, sql, Tuple.tuple().addLong(id)).mapEmpty();
    }

    public Future<Long> existsByName(String name) {
        String sql = "SELECT COUNT(*) as count FROM scheduled_tasks WHERE name = $1";
        return DatabaseVerticle.query(vertx, sql, Tuple.tuple().addString(name))
            .map(rows -> rows.iterator().next().getLong("count"));
    }

    public Future<Boolean> existsByNameAndNotId(String name, Long excludeId) {
        String sql = "SELECT COUNT(*) as count FROM scheduled_tasks WHERE name = $1 AND id != $2";
        return DatabaseVerticle.query(vertx, sql, Tuple.tuple().addString(name).addLong(excludeId))
            .map(rows -> rows.iterator().next().getLong("count") > 0);
    }
}