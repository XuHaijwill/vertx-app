package com.example.auth;

import com.example.db.DatabaseVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Permission service — RuoYi's @Service("ss") PermissionService equivalent.
 *
 * <p>Provides programmatic permission/role checks usable at both the route
 * level and inside service-layer code, similar to how RuoYi's
 * {@code @ss.hasPermi()} SpEL expression works.</p>
 *
 * <h3>RuoYi → vertx-app mapping:</h3>
 * <pre>
 * // RuoYi Spring controller
 * @PreAuthorize("@ss.hasPermi('system:user:list')")
 * // vertx-app route level
 * router.get("/api/users")
 *     .handler(authHandler)                          // JWT validation
 *     .handler(RequirePermission.of("system:user:list"))  // permission check
 *     .handler(userApi::listUsers);
 *
 * // vertx-app in service layer
 * if (!permissionService.hasPermi(ctx, "system:user:list")) {
 *     throw new BusinessException("Insufficient permissions");
 * }
 * </pre>
 */
public class PermissionService {

    private static final Logger LOG = LoggerFactory.getLogger(PermissionService.class);
    private static final String CTX_KEY = "authUser";

    private final Vertx vertx;

    // In-memory permission cache: userId → Set<permission>
    private final ConcurrentHashMap<Long, Set<String>> permissionCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<String>> roleCache = new ConcurrentHashMap<>();

    public PermissionService(Vertx vertx) {
        this.vertx = vertx;
    }

    private static PermissionService _instance;

    /** Singleton access */
    public static PermissionService getInstance(Vertx vertx) {
        if (_instance == null) {
            _instance = new PermissionService(vertx);
        }
        return _instance;
    }

    // ========== Core permission check methods (RuoYi signatures) ==========

    /**
     * @see RequirePermission#of(String...)
     */
    public boolean hasPermi(RoutingContext ctx, String permission) {
        if (permission == null || permission.isBlank()) return false;
        AuthUser user = ctx.get(CTX_KEY);
        return user != null && user.hasPermi(permission);
    }

    /**
     * @see RequirePermission#ofAnyPermi(String...)
     */
    public boolean hasAnyPermi(RoutingContext ctx, String... permissions) {
        if (permissions == null || permissions.length == 0) return false;
        AuthUser user = ctx.get(CTX_KEY);
        return user != null && user.hasAnyPermi(permissions);
    }

    /**
     * @see RequirePermission#ofAllPermi(String...)
     */
    public boolean hasAllPermi(RoutingContext ctx, String... permissions) {
        if (permissions == null || permissions.length == 0) return true;
        AuthUser user = ctx.get(CTX_KEY);
        return user != null && user.hasAllPermi(permissions);
    }

    /** Inverse of hasPermi */
    public boolean lacksPermi(RoutingContext ctx, String permission) {
        return !hasPermi(ctx, permission);
    }

    /**
     * @see RequirePermission#ofRole(String)
     */
    public boolean hasRole(RoutingContext ctx, String role) {
        if (role == null || role.isBlank()) return false;
        AuthUser user = ctx.get(CTX_KEY);
        return user != null && user.hasRole(role);
    }

    /**
     * @see RequirePermission#ofAnyRole(String...)
     */
    public boolean hasAnyRole(RoutingContext ctx, String... roles) {
        if (roles == null || roles.length == 0) return false;
        AuthUser user = ctx.get(CTX_KEY);
        return user != null && user.hasAnyRole(roles);
    }

    /** Inverse of hasRole */
    public boolean lacksRole(RoutingContext ctx, String role) {
        return !hasRole(ctx, role);
    }

    // ========== Permission loading from DB ==========

    /**
     * Load permissions from DB for the current user and merge into AuthUser.
     * Called after JWT validation to enrich with DB permissions.
     *
     * <pre>
     * SQL: SELECT DISTINCT m.perms FROM sys_menu m
     *      JOIN sys_role_menu rm ON m.menu_id = rm.menu_id
     *      JOIN sys_user_role ur ON rm.role_id = ur.role_id
     *      WHERE ur.user_id = ? AND m.perms IS NOT NULL AND m.type = 'C'
     * </pre>
     */
    public Future<AuthUser> loadUserPermissions(RoutingContext ctx) {
        AuthUser user = ctx.get(CTX_KEY);
        if (user == null) return Future.succeededFuture(null);
        if (user.getUserId() == null) return Future.succeededFuture(user);

        return loadPermissionsForUser(user.getUserId())
            .onSuccess(perms -> user.addPermissions(perms))
            .map(user);
    }

    /**
     * Load all permission strings for a user from DB.
     */
    public Future<Set<String>> loadPermissionsForUser(Long userId) {
        if (userId == null) return Future.succeededFuture(Collections.emptySet());

        return DatabaseVerticle.query(vertx,
            "SELECT DISTINCT m.perms FROM sys_menu m " +
            "JOIN sys_role_menu rm ON m.menu_id = rm.menu_id " +
            "JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
            "WHERE ur.user_id = $1 AND m.perms IS NOT NULL AND m.perms <> '' AND m.type = 'C'",
            Tuple.of(userId)
        ).map(rows -> {
            Set<String> perms = new HashSet<>();
            for (var row : rows) {
                String p = row.getString("perms");
                if (p != null && !p.isBlank()) perms.add(p.trim());
            }
            return perms;
        }).onFailure(err ->
            LOG.error("Failed to load permissions for userId={}: {}", userId, err.getMessage())
        );
    }

    /**
     * Load all role keys for a user from DB.
     */
    public Future<Set<String>> loadRolesForUser(Long userId) {
        if (userId == null) return Future.succeededFuture(Collections.emptySet());

        return DatabaseVerticle.query(vertx,
            "SELECT DISTINCT r.role_key FROM sys_role r " +
            "JOIN sys_user_role ur ON r.role_id = ur.role_id " +
            "WHERE ur.user_id = $1 AND r.status = '0'",
            Tuple.of(userId)
        ).map(rows -> {
            Set<String> roles = new HashSet<>();
            for (var row : rows) {
                String r = row.getString("role_key");
                if (r != null && !r.isBlank()) roles.add(r.trim());
            }
            return roles;
        }).onFailure(err ->
            LOG.error("Failed to load roles for userId={}: {}", userId, err.getMessage())
        );
    }

    // ========== Cache management ==========

    public void cacheUserPermissions(Long userId, Set<String> permissions) {
        permissionCache.put(userId, new HashSet<>(permissions));
    }

    public void cacheUserRoles(Long userId, Set<String> roles) {
        roleCache.put(userId, new HashSet<>(roles));
    }

    public Set<String> getCachedPermissions(Long userId) {
        return permissionCache.get(userId);
    }

    public Set<String> getCachedRoles(Long userId) {
        return roleCache.get(userId);
    }

    /** Evict permission and role cache for a specific user */
    public void evictUserCache(Long userId) {
        permissionCache.remove(userId);
        roleCache.remove(userId);
        LOG.debug("Evicted permission/role cache for userId={}", userId);
    }

    /** Clear all cached permissions and roles */
    public void clearAllCache() {
        permissionCache.clear();
        roleCache.clear();
        LOG.info("Cleared all permission/role caches");
    }

    // ========== Admin helpers ==========

    /** Get all unique permission strings from sys_menu table (type='C') */
    public Future<List<String>> getAllDefinedPermissions() {
        return DatabaseVerticle.query(vertx,
            "SELECT DISTINCT perms FROM sys_menu WHERE perms IS NOT NULL AND perms <> '' AND type = 'C'",
            Tuple.tuple()
        ).map(rows -> {
            List<String> perms = new ArrayList<>();
            for (var row : rows) {
                String p = row.getString("perms");
                if (p != null && !p.isBlank()) perms.add(p.trim());
            }
            return perms;
        });
    }

    /** Get all active role keys from sys_role table */
    public Future<List<String>> getAllRoleKeys() {
        return DatabaseVerticle.query(vertx,
            "SELECT role_key FROM sys_role WHERE status = '0' ORDER BY role_sort",
            Tuple.tuple()
        ).map(rows -> {
            List<String> roles = new ArrayList<>();
            for (var row : rows) {
                String r = row.getString("role_key");
                if (r != null && !r.isBlank()) roles.add(r.trim());
            }
            return roles;
        });
    }

    /** Batch load permissions for multiple user IDs */
    public Future<Map<Long, Set<String>>> batchLoadPermissions(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Future.succeededFuture(Collections.emptyMap());
        }

        String ph = userIds.stream()
            .map(u -> "$" + (userIds.indexOf(u) + 1))
            .collect(Collectors.joining(","));

        String sql = "SELECT ur.user_id, m.perms FROM sys_menu m " +
            "JOIN sys_role_menu rm ON m.menu_id = rm.menu_id " +
            "JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
            "WHERE ur.user_id IN (" + ph + ") AND m.perms IS NOT NULL AND m.perms <> '' AND m.type = 'C'";

        Tuple tuple = Tuple.tuple();
        userIds.forEach(tuple::addValue);

        return DatabaseVerticle.query(vertx, sql, tuple).map(rows -> {
            Map<Long, Set<String>> result = new HashMap<>();
            userIds.forEach(uid -> result.put(uid, new HashSet<>()));
            for (var row : rows) {
                Long uid = row.getLong("user_id");
                String p = row.getString("perms");
                if (uid != null && p != null && !p.isBlank()) {
                    result.get(uid).add(p.trim());
                }
            }
            return result;
        });
    }
}
