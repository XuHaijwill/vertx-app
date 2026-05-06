package com.example.service.impl;

import com.example.core.BusinessException;
import com.example.core.PageResult;
import com.example.db.AuditAction;
import com.example.db.AuditLogger;
import com.example.db.DatabaseVerticle;
import com.example.entity.SysMenu;
import com.example.repository.SysMenuRepository;
import com.example.service.SysMenuService;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

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

    // ================================================================
    // Helper: SysMenu list → JsonObject list
    // ================================================================

    private List<JsonObject> toJsonObjectList(List<SysMenu> menus) {
        return menus.stream().map(SysMenu::toJson).collect(Collectors.toList());
    }

    @Override
    public Future<List<JsonObject>> findAll() {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return repo.findAll().map(this::toJsonObjectList);
    }

    @Override
    public Future<List<JsonObject>> findMenuTree() {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return repo.findMenuTree().map(this::toJsonObjectList);
    }

    @Override
    public Future<JsonObject> findById(Long id) {
        if (!dbAvailable) return Future.succeededFuture(null);
        return repo.findById(id)
            .map(menu -> {
                if (menu == null) throw BusinessException.notFound("Menu");
                return menu.toJson();
            });
    }

    @Override
    public Future<List<JsonObject>> findByParentId(Long parentId) {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return repo.findByParentId(parentId).map(this::toJsonObjectList);
    }

    @Override
    public Future<List<JsonObject>> findVisibleMenus() {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return repo.findVisibleMenus().map(this::toJsonObjectList);
    }

    @Override
    public Future<JsonObject> create(SysMenu menu) {
        if (menu == null) {
            return Future.failedFuture(BusinessException.badRequest("Request body is required"));
        }
        String menuName = menu.getMenuName();
        if (menuName == null || menuName.isBlank()) {
            return Future.failedFuture(BusinessException.badRequest("menuName is required"));
        }

        Long parentId = menu.getParentId() != null ? menu.getParentId() : 0L;

        if (!dbAvailable) {
            return Future.succeededFuture(menu.toJson().put("menuId", System.currentTimeMillis()));
        }

        return repo.existsByNameUnderParent(menuName, parentId)
            .compose(exists -> {
                if (exists) {
                    return Future.<SysMenu>failedFuture(
                        BusinessException.conflict("Menu name already exists under this parent"));
                }
                return repo.create(menu);
            })
            .compose(created -> audit.log(
                    AuditAction.AUDIT_CREATE, "sys_menu",
                    String.valueOf(created.getMenuId()),
                    null, created.toJson())
                .map(created.toJson()));
    }

    @Override
    public Future<JsonObject> update(Long id, SysMenu menu) {
        if (!dbAvailable) {
            return Future.succeededFuture(menu.toJson().put("menuId", id));
        }
        return repo.findById(id)
            .compose((SysMenu existing) -> {
                if (existing == null) {
                    return Future.<JsonObject>failedFuture(BusinessException.notFound("Menu"));
                }
                Long newParentId = menu.getParentId();
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
                            return doUpdateAndConvert(id, menu, existing);
                        });
                }
                return doUpdateAndConvert(id, menu, existing);
            });
    }

    private Future<SysMenu> doUpdate(Long id, SysMenu menu, SysMenu existing) {
        return repo.update(id, menu)
            .compose(updated -> audit.log(
                    AuditAction.AUDIT_UPDATE, "sys_menu",
                    String.valueOf(id),
                    existing.toJson(), updated.toJson())
                .map(updated));
    }

    // update() 需要最后 SysMenu → JsonObject
    private Future<JsonObject> doUpdateAndConvert(Long id, SysMenu menu, SysMenu existing) {
        return doUpdate(id, menu, existing).map(SysMenu::toJson);
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
                                    existing.toJson(), null)
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
                .map(list -> new PageResult<>(toJsonObjectList(list), total, page, size)));
    }

    @Override
    public Future<Long> count() {
        if (!dbAvailable) return Future.succeededFuture(0L);
        return repo.count().map(c -> c.longValue());
    }
}
