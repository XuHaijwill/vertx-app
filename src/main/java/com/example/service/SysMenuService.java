package com.example.service;

import com.example.core.PageResult;
import com.example.entity.SysMenu;
import io.vertx.core.Future;

import java.util.List;

/**
 * System Menu Service Interface
 */
public interface SysMenuService {

    Future<List<SysMenu>> findAll();
    Future<List<SysMenu>> findMenuTree();
    Future<SysMenu> findById(Long id);
    Future<List<SysMenu>> findByParentId(Long parentId);
    Future<List<SysMenu>> findVisibleMenus();
    Future<SysMenu> create(SysMenu menu);
    Future<SysMenu> update(Long id, SysMenu menu);
    Future<Void> delete(Long id);
    Future<Boolean> hasChildren(Long menuId);
    Future<Boolean> existsByNameUnderParent(String menuName, Long parentId);
    Future<PageResult<SysMenu>> findPaginated(int page, int size);
    Future<Long> count();
}