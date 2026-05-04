package com.example.repository;

import com.example.db.DatabaseVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.util.List;

/**
 * SysMenu Repository - Database operations for sys_menu table
 */
public class SysMenuRepository {

    private final Vertx vertx;

    public SysMenuRepository(Vertx vertx) {
        this.vertx = vertx;
    }

    // ================================================================
    // QUERY OPERATIONS
    // ================================================================

    /**
     * Find all menus
     */
    public Future<List<JsonObject>> findAll() {
        String sql = "SELECT * FROM sys_menu ORDER BY parent_id, order_num, menu_id";
        return DatabaseVerticle.query(vertx, sql)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Find menu by ID
     */
    public Future<JsonObject> findById(Long menuId) {
        String sql = "SELECT * FROM sys_menu WHERE menu_id = $1";
        Tuple params = Tuple.tuple().addLong(menuId);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    /**
     * Find menus by parent_id
     */
    public Future<List<JsonObject>> findByParentId(Long parentId) {
        String sql = "SELECT * FROM sys_menu WHERE parent_id = $1 ORDER BY order_num, menu_id";
        Tuple params = Tuple.tuple().addLong(parentId);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Find root menus (parent_id = 0)
     */
    public Future<List<JsonObject>> findRootMenus() {
        String sql = "SELECT * FROM sys_menu WHERE parent_id = 0 ORDER BY order_num, menu_id";
        return DatabaseVerticle.query(vertx, sql)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Find menus by menu_type
     */
    public Future<List<JsonObject>> findByMenuType(String menuType) {
        String sql = "SELECT * FROM sys_menu WHERE menu_type = $1 ORDER BY parent_id, order_num, menu_id";
        Tuple params = Tuple.tuple().addString(menuType);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Find menus by status
     */
    public Future<List<JsonObject>> findByStatus(String status) {
        String sql = "SELECT * FROM sys_menu WHERE status = $1 ORDER BY parent_id, order_num, menu_id";
        Tuple params = Tuple.tuple().addString(status);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Find visible menus (visible = '0')
     */
    public Future<List<JsonObject>> findVisibleMenus() {
        String sql = "SELECT * FROM sys_menu WHERE visible = '0' AND status = '0' ORDER BY parent_id, order_num, menu_id";
        return DatabaseVerticle.query(vertx, sql)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Find menus by name (fuzzy search)
     */
    public Future<List<JsonObject>> findByMenuName(String menuName) {
        if (menuName == null || menuName.isEmpty()) {
            return findAll();
        }
        String sql = "SELECT * FROM sys_menu WHERE menu_name LIKE $1 ORDER BY parent_id, order_num, menu_id";
        Tuple params = Tuple.tuple().addString("%" + menuName + "%");
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Advanced search with multiple filters
     */
    public Future<List<JsonObject>> search(String menuName, String menuType, 
                                           String status, String visible) {
        StringBuilder sql = new StringBuilder("SELECT * FROM sys_menu WHERE 1=1");
        Tuple params = Tuple.tuple();
        int paramIndex = 1;

        if (menuName != null && !menuName.isEmpty()) {
            sql.append(" AND menu_name LIKE $").append(paramIndex++);
            params.addString("%" + menuName + "%");
        }
        if (menuType != null && !menuType.isEmpty()) {
            sql.append(" AND menu_type = $").append(paramIndex++);
            params.addString(menuType);
        }
        if (status != null && !status.isEmpty()) {
            sql.append(" AND status = $").append(paramIndex++);
            params.addString(status);
        }
        if (visible != null && !visible.isEmpty()) {
            sql.append(" AND visible = $").append(paramIndex++);
            params.addString(visible);
        }
        sql.append(" ORDER BY parent_id, order_num, menu_id");

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Count all menus
     */
    public Future<Long> count() {
        String sql = "SELECT COUNT(*) as count FROM sys_menu";
        return DatabaseVerticle.query(vertx, sql)
            .map(rows -> rows.iterator().next().getLong("count"));
    }

    /**
     * Count children by parent_id
     */
    public Future<Long> countByParentId(Long parentId) {
        String sql = "SELECT COUNT(*) as count FROM sys_menu WHERE parent_id = $1";
        Tuple params = Tuple.tuple().addLong(parentId);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getLong("count"));
    }

    /**
     * Check if menu has children
     */
    public Future<Boolean> hasChildren(Long menuId) {
        String sql = "SELECT COUNT(*) as count FROM sys_menu WHERE parent_id = $1";
        Tuple params = Tuple.tuple().addLong(menuId);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getLong("count") > 0);
    }

    /**
     * Check if menu_name exists under same parent
     */
    public Future<Boolean> existsByNameAndParent(String menuName, Long parentId) {
        String sql = "SELECT COUNT(*) as count FROM sys_menu WHERE menu_name = $1 AND parent_id = $2";
        Tuple params = Tuple.tuple().addString(menuName).addLong(parentId);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getLong("count") > 0);
    }

    /**
     * Check if menu_name exists under same parent excluding specific ID
     */
    public Future<Boolean> existsByNameAndParentExcludeId(String menuName, Long parentId, Long excludeId) {
        String sql = "SELECT COUNT(*) as count FROM sys_menu WHERE menu_name = $1 AND parent_id = $2 AND menu_id != $3";
        Tuple params = Tuple.tuple().addString(menuName).addLong(parentId).addLong(excludeId);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getLong("count") > 0);
    }

    /**
     * Alias for existsByNameAndParent - check if menu name exists under same parent
     */
    public Future<Boolean> existsByNameUnderParent(String menuName, Long parentId) {
        return existsByNameAndParent(menuName, parentId);
    }

    /**
     * Find all ancestor menu IDs (for circular reference check)
     */
    public Future<java.util.List<Long>> findAllAncestorIds(Long menuId) {
        // Recursive CTE to find all ancestors
        String sql = """
            WITH RECURSIVE ancestors AS (
                SELECT menu_id, parent_id FROM sys_menu WHERE menu_id = $1
                UNION ALL
                SELECT m.menu_id, m.parent_id FROM sys_menu m
                INNER JOIN ancestors a ON m.menu_id = a.parent_id
            )
            SELECT menu_id FROM ancestors WHERE menu_id != $1
            """;
        Tuple params = Tuple.tuple().addLong(menuId);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                java.util.List<Long> ids = new java.util.ArrayList<>();
                for (Row row : rows) {
                    ids.add(row.getLong("menu_id"));
                }
                return ids;
            });
    }

    /**
     * Build menu tree from flat list
     */
    public Future<List<JsonObject>> findMenuTree() {
        return findAll()
            .map(menus -> {
                java.util.Map<Long, JsonObject> menuMap = new java.util.HashMap<>();
                java.util.List<JsonObject> roots = new java.util.ArrayList<>();
                
                // First pass: create map
                for (JsonObject menu : menus) {
                    menuMap.put(menu.getLong("menuId"), menu.copy());
                }
                
                // Second pass: build tree
                for (JsonObject menu : menus) {
                    Long parentId = menu.getLong("parentId");
                    if (parentId == null || parentId == 0) {
                        roots.add(menuMap.get(menu.getLong("menuId")));
                    } else {
                        JsonObject parent = menuMap.get(parentId);
                        if (parent != null) {
                            java.util.List<JsonObject> children = parent.getJsonArray("children", new io.vertx.core.json.JsonArray()).getList();
                            children.add(menuMap.get(menu.getLong("menuId")));
                            parent.put("children", new io.vertx.core.json.JsonArray(children));
                        }
                    }
                }
                
                return roots;
            });
    }

    // ================================================================
    // MUTATION OPERATIONS
    // ================================================================

    /**
     * Create a new menu
     */
    public Future<JsonObject> create(JsonObject menu) {
        String sql = """
            INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, 
                                  query, route_name, is_frame, is_cache, menu_type, 
                                  visible, status, perms, icon, create_by, remark)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16)
            RETURNING *
            """;
        Tuple params = Tuple.tuple()
            .addString(menu.getString("menuName"))
            .addLong(menu.getLong("parentId", 0L))
            .addInteger(menu.getInteger("orderNum", 0))
            .addString(menu.getString("path", ""))
            .addString(menu.getString("component"))
            .addString(menu.getString("query"))
            .addString(menu.getString("routeName", ""))
            .addInteger(menu.getInteger("isFrame", 1))
            .addInteger(menu.getInteger("isCache", 0))
            .addString(menu.getString("menuType", "M"))
            .addString(menu.getString("visible", "0"))
            .addString(menu.getString("status", "0"))
            .addString(menu.getString("perms"))
            .addString(menu.getString("icon", "#"))
            .addString(menu.getString("createBy", "admin"))
            .addString(menu.getString("remark", ""));
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    /**
     * Update an existing menu
     */
    public Future<JsonObject> update(Long menuId, JsonObject menu) {
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
            .addString(menu.getString("menuName"))
            .addLong(menu.getLong("parentId"))
            .addInteger(menu.getInteger("orderNum"))
            .addString(menu.getString("path"))
            .addString(menu.getString("component"))
            .addString(menu.getString("query"))
            .addString(menu.getString("routeName"))
            .addInteger(menu.getInteger("isFrame"))
            .addInteger(menu.getInteger("isCache"))
            .addString(menu.getString("menuType"))
            .addString(menu.getString("visible"))
            .addString(menu.getString("status"))
            .addString(menu.getString("perms"))
            .addString(menu.getString("icon"))
            .addString(menu.getString("updateBy"))
            .addString(menu.getString("remark"));
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    /**
     * Delete a menu by ID
     */
    public Future<Boolean> delete(Long menuId) {
        String sql = "DELETE FROM sys_menu WHERE menu_id = $1 RETURNING menu_id";
        Tuple params = Tuple.tuple().addLong(menuId);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.rowCount() > 0);
    }

    /**
     * Delete multiple menus by IDs
     */
    public Future<Integer> deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Future.succeededFuture(0);
        String placeholders = ids.stream().map(i -> "$" + (ids.indexOf(i) + 1)).collect(java.util.stream.Collectors.joining(","));
        String sql = "DELETE FROM sys_menu WHERE menu_id IN (" + placeholders + ")";
        Tuple params = Tuple.tuple();
        for (Long id : ids) params.addLong(id);
        return DatabaseVerticle.query(vertx, sql, params).map(rows -> rows.rowCount());
    }

    // ================================================================
    // PAGINATION
    // ================================================================

    /**
     * Find with pagination
     */
    public Future<List<JsonObject>> findPaginated(int page, int size) {
        int offset = (page - 1) * size;
        String sql = "SELECT * FROM sys_menu ORDER BY parent_id, order_num, menu_id LIMIT $1 OFFSET $2";
        Tuple params = Tuple.tuple().addInteger(size).addInteger(offset);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Advanced search with pagination
     */
    public Future<List<JsonObject>> searchPaginated(String menuName, String menuType,
                                                     String status, String visible,
                                                     int page, int size) {
        int offset = (page - 1) * size;
        StringBuilder sql = new StringBuilder("SELECT * FROM sys_menu WHERE 1=1");
        Tuple params = Tuple.tuple();
        int paramIndex = 1;

        if (menuName != null && !menuName.isEmpty()) {
            sql.append(" AND menu_name LIKE $").append(paramIndex++);
            params.addString("%" + menuName + "%");
        }
        if (menuType != null && !menuType.isEmpty()) {
            sql.append(" AND menu_type = $").append(paramIndex++);
            params.addString(menuType);
        }
        if (status != null && !status.isEmpty()) {
            sql.append(" AND status = $").append(paramIndex++);
            params.addString(status);
        }
        if (visible != null && !visible.isEmpty()) {
            sql.append(" AND visible = $").append(paramIndex++);
            params.addString(visible);
        }
        sql.append(" ORDER BY parent_id, order_num, menu_id LIMIT $").append(paramIndex++).append(" OFFSET $").append(paramIndex);
        params.addInteger(size).addInteger(offset);

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Count search results (for pagination total)
     */
    public Future<Long> searchCount(String menuName, String menuType,
                                     String status, String visible) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) as count FROM sys_menu WHERE 1=1");
        Tuple params = Tuple.tuple();
        int paramIndex = 1;

        if (menuName != null && !menuName.isEmpty()) {
            sql.append(" AND menu_name LIKE $").append(paramIndex++);
            params.addString("%" + menuName + "%");
        }
        if (menuType != null && !menuType.isEmpty()) {
            sql.append(" AND menu_type = $").append(paramIndex++);
            params.addString(menuType);
        }
        if (status != null && !status.isEmpty()) {
            sql.append(" AND status = $").append(paramIndex++);
            params.addString(status);
        }
        if (visible != null && !visible.isEmpty()) {
            sql.append(" AND visible = $").append(paramIndex++);
            params.addString(visible);
        }

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(rows -> rows.iterator().next().getLong("count"));
    }
}
