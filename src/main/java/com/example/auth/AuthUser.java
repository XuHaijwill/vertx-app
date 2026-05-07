package com.example.auth;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Authenticated user context — equivalent to RuoYi's LoginUser.
 *
 * <p>Holds the authenticated user's identity and all their permissions.
 * Stored in RoutingContext by KeycloakAuthHandler after JWT validation.</p>
 *
 * <p>Permission format (RuoYi convention):</p>
 * <ul>
 *   <li>system:user:list    — list/query users</li>
 *   <li>system:user:add     — create users</li>
 *   <li>system:user:edit    — update users</li>
 *   <li>system:user:remove  — delete users</li>
 *   <li>system:user:export  — export users</li>
 *   <li>system:user:resetPwd — reset password</li>
 * </ul>
 *
 * <p>Role format:</p>
 * <ul>
 *   <li>admin    — super admin</li>
 *   <li>common   — regular user</li>
 * </ul>
 */
public class AuthUser {

    /** Super admin role key — bypasses all permission checks */
    public static final String SUPER_ADMIN = "admin";

    /** Wildcard permission — grants all permissions */
    public static final String ALL_PERMISSION = "*:*:*";

    private Long userId;
    private String username;
    private String email;
    private String name;
    private Long deptId;
    private String deptName;
    private Set<String> roles;       // role keys, e.g. ["admin", "common"]
    private Set<String> permissions;  // permission strings, e.g. ["system:user:list"]
    private JsonObject rawToken;     // original JWT payload

    public AuthUser() {
        this.roles = new HashSet<>();
        this.permissions = new HashSet<>();
    }

    public AuthUser(Long userId, String username) {
        this();
        this.userId = userId;
        this.username = username;
    }

    // ========== Builder-style setters (fluent) ==========

    public AuthUser setUserId(Long userId) {
        this.userId = userId;
        return this;
    }

    public AuthUser setUsername(String username) {
        this.username = username;
        return this;
    }

    public AuthUser setEmail(String email) {
        this.email = email;
        return this;
    }

    public AuthUser setName(String name) {
        this.name = name;
        return this;
    }

    public AuthUser setDeptId(Long deptId) {
        this.deptId = deptId;
        return this;
    }

    public AuthUser setDeptName(String deptName) {
        this.deptName = deptName;
        return this;
    }

    public AuthUser setRoles(Set<String> roles) {
        this.roles = roles != null ? roles : new HashSet<>();
        return this;
    }

    public AuthUser setPermissions(Set<String> permissions) {
        this.permissions = permissions != null ? permissions : new HashSet<>();
        return this;
    }

    public AuthUser setRawToken(JsonObject rawToken) {
        this.rawToken = rawToken;
        return this;
    }

    // ========== Getters ==========

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public Long getDeptId() {
        return deptId;
    }

    public String getDeptName() {
        return deptName;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public JsonObject getRawToken() {
        return rawToken;
    }

    // ========== Convenience ==========

    public boolean isAdmin() {
        return roles != null && (roles.contains(SUPER_ADMIN) || roles.contains("admin"));
    }

    public boolean hasRole(String role) {
        if (roles == null || role == null) return false;
        return roles.contains(SUPER_ADMIN) || roles.contains(role.trim());
    }

    public boolean hasAnyRole(String... roleArray) {
        if (roles == null || roleArray == null) return false;
        return Arrays.stream(roleArray)
            .anyMatch(r -> roles.contains(SUPER_ADMIN) || roles.contains(r.trim()));
    }

    /**
     * Check if user has a specific permission.
     * Supports wildcard: "*:*:*" grants all permissions.
     * Supports simple match: "system:user:*" matches "system:user:list".
     */
    public boolean hasPermi(String permission) {
        if (permissions == null || permission == null) return false;
        if (permissions.contains(ALL_PERMISSION)) return true;
        String p = permission.trim();
        // Wildcard match: "system:user:*" matches "system:user:list"
        if (p.contains("*")) {
            String regex = p.replace("*", ".*");
            return permissions.stream().anyMatch(perm -> perm.matches(regex));
        }
        return permissions.contains(p);
    }

    /**
     * Check if user has ALL of the given permissions.
     */
    public boolean hasAllPermi(String... perms) {
        if (perms == null) return true;
        for (String p : perms) {
            if (!hasPermi(p)) return false;
        }
        return true;
    }

    /**
     * Check if user has ANY of the given permissions.
     */
    public boolean hasAnyPermi(String... perms) {
        if (perms == null) return false;
        return Arrays.stream(perms).anyMatch(this::hasPermi);
    }

    /**
     * Add permissions (e.g. loaded from DB after JWT validation).
     */
    public AuthUser addPermissions(Collection<String> perms) {
        if (perms != null) {
            this.permissions.addAll(perms);
        }
        return this;
    }

    /**
     * Add roles.
     */
    public AuthUser addRoles(Collection<String> roleList) {
        if (roleList != null) {
            this.roles.addAll(roleList);
        }
        return this;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (userId != null) json.put("userId", userId);
        if (username != null) json.put("username", username);
        if (email != null) json.put("email", email);
        if (name != null) json.put("name", name);
        if (deptId != null) json.put("deptId", deptId);
        if (deptName != null) json.put("deptName", deptName);
        json.put("roles", new JsonArray(new ArrayList<>(roles)));
        json.put("permissions", new JsonArray(new ArrayList<>(permissions)));
        return json;
    }

    @Override
    public String toString() {
        return "AuthUser{" +
            "userId=" + userId +
            ", username='" + username + '\'' +
            ", roles=" + roles +
            ", permissions=" + permissions +
            '}';
    }
}
