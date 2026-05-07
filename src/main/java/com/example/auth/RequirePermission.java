package com.example.auth;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Vert.x equivalent of RuoYi's @PreAuthorize annotation.
 *
 * <p>Provides declarative permission checking on routes. Must be mounted
 * AFTER KeycloakAuthHandler in the route chain.</p>
 *
 * <h3>Usage:</h3>
 * <pre>
 * router.get("/api/users")
 *     .handler(KeycloakAuthHandler.create(vertx, config))     // 1. authenticate
 *     .handler(RequirePermission.of("system:user:list"))     // 2. authorize
 *     .handler(userApi::listUsers);
 *
 * router.post("/api/users")
 *     .handler(KeycloakAuthHandler.create(vertx, config))
 *     .handler(RequirePermission.of("system:user:add"))      // create
 *     .handler(userApi::createUser);
 *
 * router.delete("/api/users/:id")
 *     .handler(KeycloakAuthHandler.create(vertx, config))
 *     .handler(RequirePermission.of("system:user:remove"))   // delete
 *     .handler(userApi::deleteUser);
 *
 * // Admin-only route
 * router.get("/api/admin/roles")
 *     .handler(KeycloakAuthHandler.create(vertx, config))
 *     .handler(RequirePermission.ofRole("admin"))             // role check
 *     .handler(roleApi::listRoles);
 * </pre>
 *
 * <h3>RuoYi SpEL → vertx-app mapping:</h3>
 * <pre>
 * @PreAuthorize("@ss.hasPermi('system:user:list')")
 *   → RequirePermission.of("system:user:list")
 *
 * @PreAuthorize("@ss.hasRole('admin')")
 *   → RequirePermission.ofRole("admin")
 *
 * @PreAuthorize("@ss.hasAnyPermi('system:user:add', 'system:user:edit')")
 *   → RequirePermission.ofAny("system:user:add", "system:user:edit")
 *
 * @PreAuthorize("@ss.hasAnyRoles('admin', 'common')")
 *   → RequirePermission.ofAnyRole("admin", "common")
 *
 * @PreAuthorize("@ss.hasAllPermi('system:user:edit', 'system:user:add')")
 *   → RequirePermission.ofAll("system:user:edit", "system:user:add")
 * </pre>
 */
public class RequirePermission implements Handler<RoutingContext> {

    /** RoutingContext key for the AuthUser */
    private static final String CTX_KEY = "authUser";

    /** SpEL expression pattern: @ss.hasPermi(...) */
    private static final Pattern SPEL_PATTERN = Pattern.compile(
        "^@ss\\.(hasPermi|hasRole|hasAnyPermi|hasAnyRoles|lacksPermi|lacksRole|hasAllPermi)\\((.*)\\)$"
    );

    // ---- Builder fields ----
    String[] requiredPerms;  // single permission check
    String[] requiredRoles;  // single role check
    String[] anyPerms;       // any of these permissions
    String[] anyRoles;       // any of these roles
    String[] allPerms;       // all permissions required
    boolean lacksPermi;       // inverse flag
    boolean lacksRole;        // inverse flag
    String[] lacksPerms;     // user must NOT have any of these
    String[] lacksRoles;      // user must NOT have any of these
    String expr = "";         // for error messages

    private RequirePermission() {}

    // ========== Static factory methods ==========

    /**
     * Single permission: hasPermi("system:user:list")
     */
    public static RequirePermission of(String... permissions) {
        RequirePermission rp = new RequirePermission();
        rp.requiredPerms = permissions;
        rp.expr = "@ss.hasPermi('" + String.join("','", permissions) + "')";
        return rp;
    }

    /**
     * Single role: hasRole("admin")
     */
    public static RequirePermission ofRole(String role) {
        RequirePermission rp = new RequirePermission();
        rp.requiredRoles = new String[]{role};
        rp.expr = "@ss.hasRole('" + role + "')";
        return rp;
    }

    /**
     * Any of these permissions: hasAnyPermi("p1", "p2")
     */
    public static RequirePermission ofAny(String... perms) {
        RequirePermission rp = new RequirePermission();
        rp.anyPerms = perms;
        rp.expr = "@ss.hasAnyPermi('" + String.join("','", perms) + "')";
        return rp;
    }

    /**
     * Any of these roles: hasAnyRoles("admin", "common")
     */
    public static RequirePermission ofAnyRole(String... roles) {
        RequirePermission rp = new RequirePermission();
        rp.anyRoles = roles;
        rp.expr = "@ss.hasAnyRoles('" + String.join("','", roles) + "')";
        return rp;
    }

    /**
     * All permissions required: hasAllPermi("p1", "p2")
     */
    public static RequirePermission ofAll(String... perms) {
        RequirePermission rp = new RequirePermission();
        rp.allPerms = perms;
        rp.expr = "@ss.hasAllPermi('" + String.join("','", perms) + "')";
        return rp;
    }

    /**
     * User must NOT have any of these permissions: lacksPermi("perm")
     */
    public static RequirePermission lacks(String... perms) {
        RequirePermission rp = of(perms);
        rp.lacksPermi = true;
        rp.lacksPerms = perms;
        rp.expr = "@ss.lacksPermi('" + String.join("','", perms) + "')";
        return rp;
    }

    /**
     * User must NOT have this role: lacksRole("admin")
     */
    public static RequirePermission lacksRole(String role) {
        RequirePermission rp = ofRole(role);
        rp.lacksRole = true;
        rp.lacksRoles = new String[]{role};
        rp.expr = "@ss.lacksRole('" + role + "')";
        return rp;
    }

    /**
     * Parse a RuoYi-style SpEL expression string.
     * E.g. "@ss.hasPermi('system:user:list')"
     */
    public static RequirePermission parse(String spelExpr) {
        if (spelExpr == null || spelExpr.isBlank()) {
            throw new IllegalArgumentException("SpEL expression cannot be null or blank");
        }
        spelExpr = spelExpr.trim();
        var matcher = SPEL_PATTERN.matcher(spelExpr);
        if (!matcher.matches()) {
            // Not a standard SpEL expression — treat as plain permission string
            String[] parts = spelExpr.split(",");
            return of(Arrays.stream(parts).map(String::trim).toArray(String[]::new));
        }

        String method = matcher.group(1);
        String argsStr = matcher.group(2);
        String[] args = parseArgs(argsStr);

        switch (method) {
            case "hasPermi":     return of(args);
            case "hasRole":      return args.length > 0 ? ofRole(args[0]) : ofRole("");
            case "hasAnyPermi":  return ofAny(args);
            case "hasAnyRoles":  return ofAnyRole(args);
            case "lacksPermi":   return lacks(args);
            case "lacksRole":    return args.length > 0 ? lacksRole(args[0]) : lacksRole("");
            case "hasAllPermi":  return ofAll(args);
            default: throw new IllegalArgumentException("Unknown SpEL method: " + method);
        }
    }

    /**
     * AND: all checks must pass. Usage: RequirePermission.of("p1").and("p2")
     */
    public RequirePermission and(String... additionalPerms) {
        String[] combined = Arrays.copyOf(requiredPerms, requiredPerms.length + additionalPerms.length);
        System.arraycopy(additionalPerms, 0, combined, requiredPerms.length, additionalPerms.length);
        RequirePermission rp = new RequirePermission();
        rp.requiredPerms = combined;
        rp.expr = this.expr + " AND @ss.hasPermi('" + String.join("','", additionalPerms) + "')";
        return rp;
    }

    /**
     * OR: any check passes. Usage: RequirePermission.ofAny("p1", "p2")
     */
    public RequirePermission or(String... otherPerms) {
        String[] combined = anyPerms != null
            ? Arrays.copyOf(anyPerms, anyPerms.length + otherPerms.length)
            : Arrays.copyOf(requiredPerms, requiredPerms.length + otherPerms.length);
        if (anyPerms != null) {
            System.arraycopy(otherPerms, 0, combined, anyPerms.length, otherPerms.length);
        } else {
            System.arraycopy(otherPerms, 0, combined, requiredPerms.length, otherPerms.length);
        }
        RequirePermission rp = new RequirePermission();
        rp.anyPerms = combined;
        rp.expr = this.expr + " OR @ss.hasPermi('" + String.join("','", otherPerms) + "')";
        return rp;
    }

    // ========== Route handler ==========

    @Override
    public void handle(RoutingContext ctx) {
        AuthUser user = ctx.get(CTX_KEY);

        if (user == null) {
            sendResponse(ctx, 401, "UNAUTHORIZED", "Authentication required");
            return;
        }

        boolean allowed = check(user);

        if (allowed) {
            ctx.next();
        } else {
            sendResponse(ctx, 403, "FORBIDDEN",
                "Insufficient permissions — required: " + expr +
                ", user permissions: " + user.getPermissions());
        }
    }

    private boolean check(AuthUser user) {
        // Super admin bypasses all permission checks
        if (user.isAdmin()) return true;

        if (lacksPerms != null && lacksPerms.length > 0) {
            return !user.hasAnyPermi(lacksPerms);
        }
        if (lacksRoles != null && lacksRoles.length > 0) {
            return !user.hasAnyRole(lacksRoles);
        }
        if (requiredPerms != null && requiredPerms.length > 0) {
            return user.hasAllPermi(requiredPerms);
        }
        if (requiredRoles != null && requiredRoles.length > 0) {
            for (String r : requiredRoles) {
                if (!user.hasRole(r)) return false;
            }
            return true;
        }
        if (anyPerms != null && anyPerms.length > 0) {
            return user.hasAnyPermi(anyPerms);
        }
        if (anyRoles != null && anyRoles.length > 0) {
            return user.hasAnyRole(anyRoles);
        }
        if (allPerms != null && allPerms.length > 0) {
            return user.hasAllPermi(allPerms);
        }
        // No requirements defined — allow
        return true;
    }

    private void sendResponse(RoutingContext ctx, int status, String code, String message) {
        ctx.response()
            .setStatusCode(status)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject()
                .put("code", code)
                .put("message", message)
                .put("timestamp", System.currentTimeMillis())
                .encode());
    }

    private static String[] parseArgs(String argsStr) {
        if (argsStr == null || argsStr.isBlank()) return new String[0];
        return Arrays.stream(argsStr.split(","))
            .map(s -> {
                s = s.trim();
                if ((s.startsWith("'") && s.endsWith("'")) ||
                    (s.startsWith("\"") && s.endsWith("\""))) {
                    s = s.substring(1, s.length() - 1);
                }
                return s.trim();
            })
            .filter(s -> !s.isEmpty())
            .toArray(String[]::new);
    }

    /** Debug: get the requirement expression */
    public String expression() {
        return expr;
    }

    /** Debug: human-readable description */
    public String description() {
        if (requiredPerms != null) return "hasPermi(" + String.join(", ", requiredPerms) + ")";
        if (requiredRoles != null) return "hasRole(" + String.join(", ", requiredRoles) + ")";
        if (anyPerms != null) return "hasAnyPermi(" + String.join(", ", anyPerms) + ")";
        if (anyRoles != null) return "hasAnyRole(" + String.join(", ", anyRoles) + ")";
        if (allPerms != null) return "hasAllPermi(" + String.join(", ", allPerms) + ")";
        return expr;
    }
}
