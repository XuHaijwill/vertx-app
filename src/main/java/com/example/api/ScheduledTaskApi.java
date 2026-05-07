package com.example.api;

import com.example.core.ApiResponse;
import com.example.core.PageResult;
import com.example.entity.ScheduledTask;
import com.example.service.ScheduledTaskService;
import com.example.service.impl.ScheduledTaskServiceImpl;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.List;

/**
 * REST API for scheduled task management.
 *
 * <pre>
 * GET  /api/scheduled-tasks              — list with pagination + filters
 * GET  /api/scheduled-tasks/:id          — get by id
 * POST /api/scheduled-tasks              — create
 * PUT  /api/scheduled-tasks/:id          — update
 * DELETE /api/scheduled-tasks/:id        — delete
 * GET  /api/scheduled-tasks/stats        — statistics
 * </pre>
 */
public class ScheduledTaskApi extends BaseApi {

    private final ScheduledTaskService service;

    public ScheduledTaskApi(Vertx vertx) {
        super(vertx);
        this.service = new ScheduledTaskServiceImpl(vertx);
    }

    @Override
    public void registerRoutes(Router router, String contextPath) {
        String prefix = contextPath == null ? "/api" : contextPath;

        router.get(prefix + "/scheduled-tasks").handler(this::list);
        router.get(prefix + "/scheduled-tasks/stats").handler(this::stats);
        router.get(prefix + "/scheduled-tasks/:id").handler(this::getById);
        router.post(prefix + "/scheduled-tasks").handler(this::create);
        router.put(prefix + "/scheduled-tasks/:id").handler(this::update);
        router.delete(prefix + "/scheduled-tasks/:id").handler(this::delete);
    }

    // ================================================================
    // GET /api/scheduled-tasks (list + pagination + filters)
    // ================================================================

    private void list(RoutingContext ctx) {
        String name = queryStr(ctx, "name");
        String taskType = queryStr(ctx, "taskType");
        String status = queryStr(ctx, "status");
        String lastRunStatus = queryStr(ctx, "lastRunStatus");
        int page = queryIntClamped(ctx, "page", 1, 1, Integer.MAX_VALUE);
        int size = queryIntClamped(ctx, "size", 20, 1, 200);

        service.search(name, taskType, status, lastRunStatus, page, size)
            .compose(list -> {
                return service.searchCount(name, taskType, status, lastRunStatus)
                    .map(count -> {
                        List<JsonObject> jsonList = toJsonList(list);
                        return new PageResult<JsonObject>(jsonList, count, page, size);
                    });
            })
            .onSuccess(r -> ctx.json(ApiResponse.success(r.toJson()).toJson()))
            .onFailure(err -> fail(ctx, err));
    }

    // ================================================================
    // GET /api/scheduled-tasks/stats
    // ================================================================

    private void stats(RoutingContext ctx) {
        service.findAll()
            .onSuccess(list -> {
                long total = list.size();
                long active = 0, paused = 0;
                long success = 0, failed = 0;
                for (ScheduledTask task : list) {
                    String s = task.getStatus();
                    if ("ACTIVE".equals(s)) active++;
                    else if ("PAUSED".equals(s)) paused++;
                    String lrs = task.getLastRunStatus();
                    if ("SUCCESS".equals(lrs)) success++;
                    else if ("FAILED".equals(lrs)) failed++;
                }
                JsonObject result = new JsonObject()
                    .put("total", total)
                    .put("active", active)
                    .put("paused", paused)
                    .put("lastRunSuccess", success)
                    .put("lastRunFailed", failed);
                ctx.json(ApiResponse.success(result).toJson());
            })
            .onFailure(err -> fail(ctx, err));
    }

    // ================================================================
    // GET /api/scheduled-tasks/:id
    // ================================================================

    private void getById(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) { badRequest(ctx, "Invalid id"); return; }

        service.findById(id)
            .onSuccess(task -> {
                if (task == null) notFound(ctx, "Scheduled task not found: " + id);
                else ctx.json(ApiResponse.success(task.toJson()).toJson());
            })
            .onFailure(err -> fail(ctx, err));
    }

    // ================================================================
    // POST /api/scheduled-tasks
    // ================================================================

    private void create(RoutingContext ctx) {
        JsonObject body = bodyJson(ctx);
        if (body == null) { badRequest(ctx, "Invalid JSON body"); return; }

        // Validate required fields
        String name = body.getString("name");
        if (name == null || name.isBlank()) {
            badRequest(ctx, "name is required"); return;
        }
        String taskType = body.getString("taskType");
        if (taskType == null || taskType.isBlank()) {
            badRequest(ctx, "taskType is required"); return;
        }

        ScheduledTask task = ScheduledTask.fromJson(body);

        // Check duplicate name
        service.existsByName(name)
            .compose(exists -> {
                if (exists) {
                    return Future.<Boolean>failedFuture("Task name already exists: " + name);
                }
                return Future.succeededFuture(false);
            })
            .compose(v -> service.create(task))
            .compose(id -> service.findById(id))
            .onSuccess(created -> ctx.json(ApiResponse.success(created.toJson()).toJson()))
            .onFailure(err -> fail(ctx, err));
    }

    // ================================================================
    // PUT /api/scheduled-tasks/:id
    // ================================================================

    private void update(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) { badRequest(ctx, "Invalid id"); return; }

        JsonObject body = bodyJson(ctx);
        if (body == null) { badRequest(ctx, "Invalid JSON body"); return; }

        String name = body.getString("name");
        if (name == null || name.isBlank()) {
            badRequest(ctx, "name is required"); return;
        }

        ScheduledTask task = ScheduledTask.fromJson(body);
        task.setId(id);

        // Check duplicate name (excluding current id)
        service.existsByNameAndNotId(name, id)
            .compose(exists -> {
                if (exists) {
                    return Future.<Boolean>failedFuture("Task name already exists: " + name);
                }
                return Future.succeededFuture(false);
            })
            .compose(v -> service.update(task))
            .compose(v -> service.findById(id))
            .onSuccess(updated -> ctx.json(ApiResponse.success(updated.toJson()).toJson()))
            .onFailure(err -> fail(ctx, err));
    }

    // ================================================================
    // DELETE /api/scheduled-tasks/:id
    // ================================================================

    private void delete(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) { badRequest(ctx, "Invalid id"); return; }

        service.delete(id)
            .onSuccess(v -> ctx.json(ApiResponse.success(new JsonObject().put("deleted", id)).toJson()))
            .onFailure(err -> fail(ctx, err));
    }

    // ================================================================
    // Helpers
    // ================================================================

    private List<JsonObject> toJsonList(List<ScheduledTask> list) {
        List<JsonObject> result = new java.util.ArrayList<>();
        for (ScheduledTask task : list) result.add(task.toJson());
        return result;
    }
}
