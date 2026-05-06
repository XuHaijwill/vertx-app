package com.example.service;

import com.example.core.PageResult;
import com.example.entity.SysMenu;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.List;

/**
 * System Menu Service Interface
 */
public interface SysMenuService {

    Future<List<JsonObject>> findAll();
    Future<List<JsonObject>> findMenuTree();
    Future<JsonObject> findById(Long id);
    Future<List<JsonObject>> findByParentId(Long parentId);
    Future<List<JsonObject>> findVisibleMenus();
    Future<JsonObject> create(SysMenu menu);
    Future<JsonObject> update(Long id, SysMenu menu);
    Future<Void> delete(Long id);
    Future<Boolean> hasChildren(Long menuId);
    Future<Boolean> existsByNameUnderParent(String menuName, Long parentId);
    Future<PageResult<JsonObject>> findPaginated(int page, int size);
    Future<Long> count();
}
