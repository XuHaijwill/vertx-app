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

    // ================================================================
    // READ
    // ================================================================

    @Override
    public Future<List<SysMenu>> findAll() {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return repo.findAll();
    }

    @Override
    public Future<List<SysMenu>> findMenuTree() {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return repo.findMenuTree();
    }

    @Override
    public Future<SysMenu> findById(Long id) {
        if (!dbAvailable) return Future.succeededFuture(null);
        return repo.findById(id)
            .map(menu -> {
                if (menu == null) throw BusinessException.notFound("Menu");
                return menu;
            });
    }

    @Override
    public Future<List<SysMenu>> findByParentId(Long parentId) {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return repo.findByParentId(parentId);
    }

    @Override
    public Future<List<SysMenu>> findVisibleMenus() {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return repo.findVisibleMenus();
    }

    // ================================================================
    // WRITE
    // ================================================================

    @Override
    public Future<SysMenu> create(SysMenu menu) {
        if (menu == null)
            throw BusinessException.badRequest("Request body is required");
        if (menu.getMenuName() == null || menu.getMenuName().isBlank())
            throw BusinessException.badRequest("menuName is required");

        Long parentId = menu.getParentId() != null ? menu.getParentId() : 0L;

        if (!dbAvailable) {
            return Future.succeededFuture(
                com.example.entity.SysMenu.fromJson(menu.toJson().put("menuId", System.currentTimeMillis())));
        }

        return repo.existsByNameUnderParent(menu.getMenuName(), parentId)
            .compose(exists -> {
                if (exists)
                    throw BusinessException.conflict("Menu name already exists under this parent");
                return repo.create(menu);
            })
            .compose(created ->
                audit.log(AuditAction.AUDIT_CREATE, "sys_menu",
                    String.valueOf(created.getMenuId()), null, created.toJson())
                    .map(created));
    }

    @Override
    public Future<SysMenu> update(Long id, SysMenu menu) {
        if (!dbAvailable) {
            return Future.succeededFuture(
                com.example.entity.SysMenu.fromJson(menu.toJson().put("menuId", id)));
        }
        return repo.findById(id)
            .compose(existing -> {
                if (existing == null)
                    throw BusinessException.notFound("Menu");
                Long newParentId = menu.getParentId();
                if (newParentId != null && newParentId.equals(id))
                    throw BusinessException.badRequest("Cannot set menu as its own parent");
                if (newParentId != null)
                    return checkCircularReference(id, newParentId)
                        .compose(isCircular -> {
                            if (isCircular)
                                throw BusinessException.badRequest("Circular reference detected");
                            return doUpdate(id, menu, existing);
                        });
                return doUpdate(id, menu, existing);
            });
    }

    private Future<SysMenu> doUpdate(Long id, SysMenu menu, SysMenu existing) {
        return repo.update(id, menu)
            .compose(updated ->
                audit.log(AuditAction.AUDIT_UPDATE, "sys_menu",
                    String.valueOf(id), existing.toJson(), updated.toJson())
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
                if (hasChildren)
                    throw BusinessException.badRequest("Cannot delete menu with children");
                return repo.findById(id)
                    .compose(existing -> {
                        if (existing == null)
                            throw BusinessException.notFound("Menu");
                        return repo.delete(id)
                            .compose(v -> audit.log(AuditAction.AUDIT_DELETE, "sys_menu",
                                String.valueOf(id), existing.toJson(), null).mapEmpty());
                    });
            });
    }

    // ================================================================
    // QUERY / COUNT
    // ================================================================

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
    public Future<PageResult<SysMenu>> findPaginated(int page, int size) {
        if (!dbAvailable)
            return Future.succeededFuture(new PageResult<>(List.of(), 0, page, size));
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