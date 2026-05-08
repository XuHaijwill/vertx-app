package com.example.repository;

import com.example.db.DatabaseVerticle;
import com.example.entity.SysMenu;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SysMenu Repository - Database operations for sys_menu table
 */
public class SysMenuRepository {

    private final Vertx vertx;

    public SysMenuRepository(Vertx vertx) {
        this.vertx = vertx;
    }

    // ================================================================
    // Row → SysMenu helpers
    // ================================================================

    private List<SysMenu> toMenuList(RowSet<Row> rows) {
        List<SysMenu> list = new ArrayList<>();
        for (Row row : rows) {
            list.add(SysMenu.toSysMenu(row));
        }
        return list;
    }

    private SysMenu toMenuOne(RowSet<Row> rows) {
        return rows.iterator().hasNext() ? SysMenu.toSysMenu(rows.iterator().next()) : null;
    }

    // ================================================================
    // QUERY OPERATIONS
    // ================================================================

    public Future<List<SysMenu>> findAll() {
        String sql = "SELECT * FROM sys_menu ORDER BY parent_id, order_num, menu_id";
        return DatabaseVerticle.query(vertx, sql).map(this::toMenuList);
    }

    public Future<SysMenu> findById(Long menuId) {
        String sql = "SELECT * FROM sys_menu WHERE menu_id = $1";
        return DatabaseVerticle.query(vertx, sql, Tuple.tuple().addLong(menuId))
            .map(this::toMenuOne);
    }

    public Future<List<SysMenu>> findByParentId(Long parentId) {
        String sql = "SELECT * FROM sys_menu WHERE parent_id = $1 ORDER BY order_num, menu_id";
        return DatabaseVerticle.query(vertx, sql, Tuple.tuple().addLong(parentId))
            .map(this::toMenuList);
    }

    public Future<List<SysMenu>> findRootMenus() {
        String sql = "SELECT * FROM sys_menu WHERE parent_id = 0 ORDER BY order_num, menu_id";
        return DatabaseVerticle.query(vertx, sql).map(this::toMenuList);
    }

    public Future<List<SysMenu>> findByMenuType(String menuType) {
        String sql = "SELECT * FROM sys_menu WHERE menu_type = $1 ORDER BY parent_id, order_num, menu_id";
        return DatabaseVerticle.query(vertx, sql, Tuple.tuple().addString(menuType))
            .map(this::toMenuList);
    }

    public Future<List<SysMenu>> findByStatus(String status) {
        String sql = "SELECT * FROM sys_menu WHERE status = $1 ORDER BY parent_id, order_num, menu_id";
        return DatabaseVerticle.query(vertx, sql, Tuple.tuple().addString(status))
            .map(this::toMenuList);
    }

    public Future<List<SysMenu>> findVisibleMenus() {
        String sql = "SELECT * FROM sys_menu WHERE visible = '0' AND status = '0' ORDER BY parent_id, order_num, menu_id";
        return DatabaseVerticle.query(vertx, sql).map(this::toMenuList);
    }

    public Future<List<SysMenu>> findByMenuName(String menuName) {
        if (menuName == null || menuName.isEmpty()) {
            return findAll();
        }
        String sql = "SELECT * FROM sys_menu WHERE menu_name LIKE $1 ORDER BY parent_id, order_num, menu_id";
        return DatabaseVerticle.query(vertx, sql, Tuple.tuple().addString("%" + menuName + "%"))
            .map(this::toMenuList);
    }

    public Future<List<SysMenu>> search(String menuName, String menuType,
                                         String status, String visible) {
        StringBuilder sql = new StringBuilder("SELECT * FROM sys_menu WHERE 1=1");
        Tuple params = Tuple.tuple();
        int idx = 1;

        if (menuName != null && !menuName.isEmpty()) {
            sql.append(" AND menu_name LIKE $").append(idx++);
            params.addString("%" + menuName + "%");
        }
        if (menuType != null && !menuType.isEmpty()) {
            sql.append(" AND menu_type = $").append(idx++);
            params.addString(menuType);
        }
        if (status != null && !status.isEmpty()) {
            sql.append(" AND status = $").append(idx++);
            params.addString(status);
        }
        if (visible != null && !visible.isEmpty()) {
            sql.append(" AND visible = $").append(idx++);
            params.addString(visible);
        }
        sql.append(" ORDER BY parent_id, order_num, menu_id");

        return DatabaseVerticle.query(vertx, sql.toString(), params).map(this::toMenuList);
    }

    public Future<Long> count() {
        String sql = "SELECT COUNT(*) as count FROM sys_menu";
        return DatabaseVerticle.query(vertx, sql)
            .map(rows -> rows.iterator().next().getLong("count"));
    }

    public Future<Long> countByParentId(Long parentId) {
        String sql = "SELECT COUNT(*) as count FROM sys_menu WHERE parent_id = $1";
        return DatabaseVerticle.query(vertx, sql, Tuple.tuple().addLong(parentId))
            .map(rows -> rows.iterator().next().getLong("count"));
    }

    public Future<Boolean> hasChildren(Long menuId) {
        String sql = "SELECT COUNT(*) as count FROM sys_menu WHERE parent_id = $1";
        return DatabaseVerticle.query(vertx, sql, Tuple.tuple().addLong(menuId))
            .map(rows -> rows.iterator().next().getLong("count") > 0);
    }

    public Future<Boolean> existsByNameAndParent(String menuName, Long parentId) {
        String sql = "SELECT COUNT(*) as count FROM sys_menu WHERE menu_name = $1 AND parent_id = $2";
        return DatabaseVerticle.query(vertx, sql, Tuple.tuple().addString(menuName).addLong(parentId))
            .map(rows -> rows.iterator().next().getLong("count") > 0);
    }

    public Future<Boolean> existsByNameAndParentExcludeId(String menuName, Long parentId, Long excludeId) {
        String sql = "SELECT COUNT(*) as count FROM sys_menu WHERE menu_name = $1 AND parent_id = $2 AND menu_id != $3";
        return DatabaseVerticle.query(vertx, sql, Tuple.tuple().addString(menuName).addLong(parentId).addLong(excludeId))
            .map(rows -> rows.iterator().next().getLong("count") > 0);
    }

    public Future<Boolean> existsByNameUnderParent(String menuName, Long parentId) {
        return existsByNameAndParent(menuName, parentId);
    }

    public Future<List<Long>> findAllAncestorIds(Long menuId) {
        String sql = """
            WITH RECURSIVE ancestors AS (
                SELECT menu_id, parent_id FROM sys_menu WHERE menu_id = $1
                UNION ALL
                SELECT m.menu_id, m.parent_id FROM sys_menu m
                INNER JOIN ancestors a ON m.menu_id = a.parent_id
            )
            SELECT menu_id FROM ancestors WHERE menu_id != $1
            """;
        return DatabaseVerticle.query(vertx, sql, Tuple.tuple().addLong(menuId))
            .map(rows -> {
                List<Long> ids = new ArrayList<>();
                for (Row row : rows) {
                    ids.add(row.getLong("menu_id"));
                }
                return ids;
            });
    }

    // ================================================================
    // MENU TREE
    // ================================================================

    public Future<List<SysMenu>> findMenuTree() {
        return findAll()
            .map(menus -> {
                java.util.Map<Long, SysMenu> menuMap = new java.util.LinkedHashMap<>();
                List<SysMenu> roots = new ArrayList<>();

                // First pass: index by menuId
                for (SysMenu m : menus) {
                    menuMap.put(m.getMenuId(), m);
                }

                // Second pass: build tree
                for (SysMenu m : menus) {
                    Long parentId = m.getParentId();
                    if (parentId == null || parentId == 0) {
                        roots.add(m);
                    } else {
                        SysMenu parent = menuMap.get(parentId);
                        if (parent != null) {
                            if (parent.getChildren() == null) {
                                parent.setChildren(new ArrayList<>());
                            }
                            parent.getChildren().add(m);
                        }
                    }
                }

                return roots;
            });
    }

    // ================================================================
    // MUTATION OPERATIONS
    // ================================================================

    public Future<SysMenu> create(SysMenu menu) {
        String sql = """
            INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component,
                                  query, route_name, is_frame, is_cache, menu_type,
                                  visible, status, perms, icon, create_by, remark)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16)
            RETURNING *
            """;
        Tuple params = Tuple.tuple()
            .addString(menu.getMenuName())
            .addLong(menu.getParentId() != null ? menu.getParentId() : 0L)
            .addInteger(menu.getOrderNum() != null ? menu.getOrderNum() : 0)
            .addString(menu.getPath() != null ? menu.getPath() : "")
            .addString(menu.getComponent())
            .addString(menu.getQuery())
            .addString(menu.getRouteName() != null ? menu.getRouteName() : "")
            .addInteger(menu.getIsFrame() != null ? menu.getIsFrame() : 1)
            .addInteger(menu.getIsCache() != null ? menu.getIsCache() : 0)
            .addString(menu.getMenuType() != null ? menu.getMenuType() : "M")
            .addString(menu.getVisible() != null ? menu.getVisible() : "0")
            .addString(menu.getStatus() != null ? menu.getStatus() : "0")
            .addString(menu.getPerms())
            .addString(menu.getIcon() != null ? menu.getIcon() : "#")
            .addString(menu.getCreateBy() != null ? menu.getCreateBy() : "admin")
            .addString(menu.getRemark() != null ? menu.getRemark() : "");
        return DatabaseVerticle.query(vertx, sql, params).map(this::toMenuOne);
    }

    public Future<SysMenu> update(Long menuId, SysMenu menu) {
        String sql = """
            UPDATE sys_menu
            SET menu_name = COALESCE($2, menu_name),
                parent_id = COALESCE($3, parent_id),
                order_num = COALESCE($4, order_num),
                path = COALESCE($5, path),
                component = COALESCE($6, component),
                query = COALESCE($7, query),
                route_name = COALESCE($8, route_name),
                is_frame = COALESCE($9, is_frame),
                is_cache = COALESCE($10, is_cache),
                menu_type = COALESCE($11, menu_type),
                visible = COALESCE($12, visible),
                status = COALESCE($13, status),
                perms = COALESCE($14, perms),
                icon = COALESCE($15, icon),
                update_by = COALESCE($16, update_by),
                remark = COALESCE($17, remark),
                update_time = CURRENT_TIMESTAMP
            WHERE menu_id = $1
            RETURNING *
            """;
        Tuple params = Tuple.tuple()
            .addLong(menuId)
            .addString(menu.getMenuName())
            .addLong(menu.getParentId())
            .addInteger(menu.getOrderNum())
            .addString(menu.getPath())
            .addString(menu.getComponent())
            .addString(menu.getQuery())
            .addString(menu.getRouteName())
            .addInteger(menu.getIsFrame())
            .addInteger(menu.getIsCache())
            .addString(menu.getMenuType())
            .addString(menu.getVisible())
            .addString(menu.getStatus())
            .addString(menu.getPerms())
            .addString(menu.getIcon())
            .addString(menu.getUpdateBy())
            .addString(menu.getRemark());
        return DatabaseVerticle.query(vertx, sql, params).map(this::toMenuOne);
    }

    public Future<Boolean> delete(Long menuId) {
        String sql = "DELETE FROM sys_menu WHERE menu_id = $1 RETURNING menu_id";
        return DatabaseVerticle.query(vertx, sql, Tuple.tuple().addLong(menuId))
            .map(rows -> rows.rowCount() > 0);
    }

    public Future<Integer> deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Future.succeededFuture(0);
        String placeholders = ids.stream().map(i -> "$" + (ids.indexOf(i) + 1)).collect(Collectors.joining(","));
        String sql = "DELETE FROM sys_menu WHERE menu_id IN (" + placeholders + ")";
        Tuple params = Tuple.tuple();
        for (Long id : ids) params.addLong(id);
        return DatabaseVerticle.query(vertx, sql, params).map(rows -> rows.rowCount());
    }

    // ================================================================
    // ROLE-BASED & USER-BASED QUERIES
    // ================================================================

    /**
     * Get all unique permission strings (perms column) from all menus.
     * Equivalent to: selectMenuPerms()
     */
    public Future<List<String>> findAllPerms() {
        String sql = "SELECT DISTINCT perms FROM sys_menu WHERE perms IS NOT NULL AND perms != ''";
        return DatabaseVerticle.query(vertx, sql).map(rows -> {
            List<String> perms = new ArrayList<>();
            for (Row row : rows) {
                String p = row.getString("perms");
                if (p != null && !p.isEmpty()) perms.add(p);
            }
            return perms;
        });
    }

    /**
     * Get permission strings for a specific role (via sys_role_menu join).
     * Equivalent to: selectMenuPermsByRoleId(roleId)
     */
    public Future<List<String>> findPermsByRoleId(Long roleId) {
        String sql = """
            SELECT DISTINCT m.perms
            FROM sys_menu m
            INNER JOIN sys_role_menu rm ON m.menu_id = rm.menu_id
            WHERE rm.role_id = $1 AND m.perms IS NOT NULL AND m.perms != ''
            """;
        return DatabaseVerticle.query(vertx, sql, Tuple.tuple().addLong(roleId)).map(rows -> {
            List<String> perms = new ArrayList<>();
            for (Row row : rows) {
                String p = row.getString("perms");
                if (p != null && !p.isEmpty()) perms.add(p);
            }
            return perms;
        });
    }

    /**
     * Get permission strings for a specific user (via user→role→menu chain).
     * Equivalent to: selectMenuPermsByUserId(userId)
     */
    public Future<List<String>> findPermsByUserId(Long userId) {
        String sql = """
            SELECT DISTINCT m.perms
            FROM sys_menu m
            INNER JOIN sys_role_menu rm ON m.menu_id = rm.menu_id
            INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id
            WHERE ur.user_id = $1 AND m.perms IS NOT NULL AND m.perms != ''
            """;
        return DatabaseVerticle.query(vertx, sql, Tuple.tuple().addLong(userId)).map(rows -> {
            List<String> perms = new ArrayList<>();
            for (Row row : rows) {
                String p = row.getString("perms");
                if (p != null && !p.isEmpty()) perms.add(p);
            }
            return perms;
        });
    }

    /**
     * Get menu IDs for a specific role, optionally respecting tree strictness.
     * Equivalent to: selectMenuListByRoleId(roleId, menuCheckStrictly)
     * @param roleId      role ID
     * @param menuCheckStrictly if true, only menus directly assigned to this role;
     *                          if false, include child menus of assigned parent menus
     */
    public Future<List<Long>> findMenuIdsByRoleId(Long roleId, boolean menuCheckStrictly) {
        if (menuCheckStrictly) {
            String sql = "SELECT menu_id FROM sys_role_menu WHERE role_id = $1";
            return DatabaseVerticle.query(vertx, sql, Tuple.tuple().addLong(roleId)).map(rows -> {
                List<Long> ids = new ArrayList<>();
                for (Row row : rows) ids.add(row.getLong("menu_id"));
                return ids;
            });
        } else {
            // Non-strict: include child menus under assigned parent menus
            String sql = """
                SELECT DISTINCT m.menu_id
                FROM sys_menu m
                INNER JOIN sys_role_menu rm ON m.menu_id = rm.menu_id
                WHERE rm.role_id = $1
                UNION
                SELECT m.menu_id
                FROM sys_menu m
                WHERE m.parent_id IN (
                    SELECT rm2.menu_id FROM sys_role_menu rm2 WHERE rm2.role_id = $1
                )
                """;
            return DatabaseVerticle.query(vertx, sql, Tuple.tuple().addLong(roleId)).map(rows -> {
                List<Long> ids = new ArrayList<>();
                for (Row row : rows) ids.add(row.getLong("menu_id"));
                return ids;
            });
        }
    }

    /**
     * Find menus by path or routeName (used for uniqueness validation).
     * Equivalent to: selectMenusByPathOrRouteName(path, routeName)
     */
    public Future<List<SysMenu>> findByPathOrRouteName(String path, String routeName) {
        if ((path == null || path.isEmpty()) && (routeName == null || routeName.isEmpty())) {
            return Future.succeededFuture(new ArrayList<>());
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM sys_menu WHERE 1=1");
        Tuple params = Tuple.tuple();
        int idx = 1;

        if (path != null && !path.isEmpty()) {
            sql.append(" AND path = $").append(idx++);
            params.addString(path);
        }
        if (routeName != null && !routeName.isEmpty()) {
            sql.append(" AND route_name = $").append(idx++);
            params.addString(routeName);
        }
        return DatabaseVerticle.query(vertx, sql.toString(), params).map(this::toMenuList);
    }

    /**
     * Update only the sort order of a menu (lightweight partial update).
     * Equivalent to: updateMenuSort(menu)
     */
    public Future<Boolean> updateSort(Long menuId, Integer orderNum, String updateBy) {
        String sql = """
            UPDATE sys_menu
            SET order_num = $2, update_by = $3, update_time = CURRENT_TIMESTAMP
            WHERE menu_id = $1
            """;
        return DatabaseVerticle.query(vertx, sql,
                Tuple.tuple().addLong(menuId).addInteger(orderNum).addString(updateBy))
            .map(rows -> rows.rowCount() > 0);
    }

    // ================================================================
    // PAGINATION
    // ================================================================

    public Future<List<SysMenu>> findPaginated(int page, int size) {
        int offset = (page - 1) * size;
        String sql = "SELECT * FROM sys_menu ORDER BY parent_id, order_num, menu_id LIMIT $1 OFFSET $2";
        return DatabaseVerticle.query(vertx, sql, Tuple.tuple().addInteger(size).addInteger(offset))
            .map(this::toMenuList);
    }

    public Future<List<SysMenu>> searchPaginated(String menuName, String menuType,
                                                   String status, String visible,
                                                   int page, int size) {
        int offset = (page - 1) * size;
        StringBuilder sql = new StringBuilder("SELECT * FROM sys_menu WHERE 1=1");
        Tuple params = Tuple.tuple();
        int idx = 1;

        if (menuName != null && !menuName.isEmpty()) {
            sql.append(" AND menu_name LIKE $").append(idx++);
            params.addString("%" + menuName + "%");
        }
        if (menuType != null && !menuType.isEmpty()) {
            sql.append(" AND menu_type = $").append(idx++);
            params.addString(menuType);
        }
        if (status != null && !status.isEmpty()) {
            sql.append(" AND status = $").append(idx++);
            params.addString(status);
        }
        if (visible != null && !visible.isEmpty()) {
            sql.append(" AND visible = $").append(idx++);
            params.addString(visible);
        }
        sql.append(" ORDER BY parent_id, order_num, menu_id LIMIT $").append(idx++).append(" OFFSET $").append(idx);
        params.addInteger(size).addInteger(offset);

        return DatabaseVerticle.query(vertx, sql.toString(), params).map(this::toMenuList);
    }

    public Future<Long> searchCount(String menuName, String menuType,
                                     String status, String visible) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) as count FROM sys_menu WHERE 1=1");
        Tuple params = Tuple.tuple();
        int idx = 1;

        if (menuName != null && !menuName.isEmpty()) {
            sql.append(" AND menu_name LIKE $").append(idx++);
            params.addString("%" + menuName + "%");
        }
        if (menuType != null && !menuType.isEmpty()) {
            sql.append(" AND menu_type = $").append(idx++);
            params.addString(menuType);
        }
        if (status != null && !status.isEmpty()) {
            sql.append(" AND status = $").append(idx++);
            params.addString(status);
        }
        if (visible != null && !visible.isEmpty()) {
            sql.append(" AND visible = $").append(idx++);
            params.addString(visible);
        }

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(rows -> rows.iterator().next().getLong("count"));
    }
}
