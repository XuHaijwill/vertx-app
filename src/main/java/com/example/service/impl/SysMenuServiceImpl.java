package com.example.service.impl;

import com.example.core.BusinessException;
import com.example.core.PageResult;
import com.example.db.AuditAction;
import com.example.db.AuditLogger;
import com.example.db.DatabaseVerticle;
import com.example.repository.SysMenuRepository;
import com.example.service.SysMenuService;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * System Menu Service Implementation
 */
public class SysMenuServiceImpl implements SysMenuService {

    private static final Logger LOG = LoggerFactory.getLogger(SysMenuServiceImpl.class);

    private final SysMenuRepository repo;
    private final AuditLogger audit;
    private final boolean dbAvailable;

    public SysMenuServiceImpl(Vertx vertx) {
        this.repo = new SysMenuRepository(vertx);
        this.audit = new AuditLogger(vertx);
        this.dbAvailable = DatabaseVerticle.getPool(vertx) != null;
    }

    @Override
    public Future<List<JsonObject>> findAll() {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return repo.findAll();
    }

    @Override
    public Future<List<JsonObject>> findMenuTree() {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return repo.findMenuTree();
    }

    @Override
    public Future<JsonObject> findById(Long id) {
        if (!dbAvailable) return Future.succeededFuture(null);
        return repo.findById(id)
            .map(menu -> {
                if (menu == null) throw BusinessException.notFound("Menu");
                return menu;
            });
    }

    @Override
    public Future<List<JsonObject>> findByParentId(Long parentId) {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return repo.findByParentId(parentId);
    }

    @Override
    public Future<List<JsonObject>> findVisibleMenus() {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return repo.findVisibleMenus();
    }

    @Override
    public Future<JsonObject> create(JsonObject menu) {
        if (menu == null) {
            return Future.failedFuture(BusinessException.badRequest("Request body is required"));
        }
        String menuName = menu.getString("menuName");
        if (menuName == null || menuName.isBlank()) {
            return Future.failedFuture(BusinessException.badRequest("menuName is required"));
        }

        Long parentId = menu.getLong("parentId", 0L);

        if (!dbAvailable) {
            return Future.succeededFuture(menu.copy().put("menuId", System.currentTimeMillis()));
        }

        // Check for duplicate name under same parent
        return repo.existsByNameUnderParent(menuName, parentId)
            .compose(exists -> {
                if (exists) {
                    return Future.<JsonObject>failedFuture(
                        BusinessException.conflict("Menu name already exists under this parent"));
                }
                return repo.create(menu)
                    .compose(created -> audit.log(
                            AuditAction.AUDIT_CREATE, "sys_menu",
                            String.valueOf(created.getLong("menuId")),
                            null, created)
                        .map(created));
            });
    }

    @Override
    public Future<JsonObject> update(Long id, JsonObject menu) {
        if (!dbAvailable) {
            return Future.succeededFuture(menu.copy().put("menuId", id));
        }
        return repo.findById(id)
            .compose(existing -> {
                if (existing == null) {
                    return Future.<JsonObject>failedFuture(BusinessException.notFound("Menu"));
                }
                // Check for circular reference
                Long newParentId = menu.getLong("parentId");
                if (newParentId != null) {
                    if (newParentId.equals(id)) {
                        return Future.<JsonObject>failedFuture(
                            BusinessException.badRequest("Cannot set menu as its own parent"));
                    }
                    return checkCircularReference(id, newParentId)
                        .compose(isCircular -> {
                            if (isCircular) {
                                return Future.<JsonObject>failedFuture(
                                    BusinessException.badRequest("Circular reference detected"));
                            }
                            return doUpdate(id, menu, existing);
                        });
                }
                return doUpdate(id, menu, existing);
            });
    }

    private Future<JsonObject> doUpdate(Long id, JsonObject menu, JsonObject existing) {
        return repo.update(id, menu)
            .compose(updated -> audit.log(
                    AuditAction.AUDIT_UPDATE, "sys_menu",
                    String.valueOf(id),
                    existing, updated)
                .map(updated));
    }

    private Future<Boolean> checkCircularReference(Long menuId, Long newParentId) {
        return repo.findAllAncestorIds(newParentId)
            .map(ancestors -> ancestors.contains(menuId));
    }

    @Override
    public Future<Void> delete(Long id) {
        if (!dbAvailable) return Future.succeededFuture();
        return repo.hasChildren(id)
            .compose(hasChildren -> {
                if (hasChildren) {
                    return Future.<Void>failedFuture(
                        BusinessException.badRequest("Cannot delete menu with children"));
                }
                return repo.findById(id)
                    .compose(existing -> {
                        if (existing == null) {
                            return Future.<Void>failedFuture(BusinessException.notFound("Menu"));
                        }
                        return repo.delete(id)
                            .compose(v -> audit.log(
                                    AuditAction.AUDIT_DELETE, "sys_menu",
                                    String.valueOf(id),
                                    existing, null)
                                .mapEmpty());
                    });
            });
    }

    @Override
    public Future<Boolean> hasChildren(Long menuId) {
        if (!dbAvailable) return Future.succeededFuture(false);
        return repo.hasChildren(menuId);
    }

    @Override
    public Future<Boolean> existsByNameUnderParent(String menuName, Long parentId) {
        if (!dbAvailable) return Future.succeededFuture(false);
        return repo.existsByNameUnderParent(menuName, parentId);
    }

    @Override
    public Future<PageResult<JsonObject>> findPaginated(int page, int size) {
        if (!dbAvailable) {
            return Future.succeededFuture(new PageResult<>(List.of(), 0, page, size));
        }
        return repo.count()
            .compose(total -> repo.findPaginated(page, size)
                .map(list -> new PageResult<>(list, total, page, size)));
    }

    @Override
    public Future<Long> count() {
        if (!dbAvailable) return Future.succeededFuture(0L);
        return repo.count().map(c -> c.longValue());
    }
}
