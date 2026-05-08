# P1: Auth 白名单清理 — 完成

## 问题

MainVerticle skipPaths 白名单过大，以下业务 API 绕过了 JWT 认证：
- `/api/users` — 用户 CRUD（危险：新增/修改/删除用户无需登录）
- `/api/products` — 商品 CRUD
- `/api/sys-configs` — 系统配置
- `/api/menus` — 菜单管理

## 修复

**Before（MainVerticle.java）：**
```java
Set<String> skipPaths = new HashSet<>(Arrays.asList(
    contextPath + "/health", contextPath + "/docs", ...
    contextPath + "/api/users",
    contextPath + "/api/products",
    contextPath + "/api/sys-configs",
    contextPath + "/api/menus"
));
```

**After：**
```java
// Only public docs/health endpoints and the auth public-key endpoint.
// All business APIs (/api/*) require a valid JWT.
// For truly public endpoints (e.g. frontend nav menus), use
// RequirePermission.publicAccess() in the route handler instead.
Set<String> skipPaths = new HashSet<>(Arrays.asList(
    contextPath + "/health",
    contextPath + "/health/",
    contextPath + "/docs",
    contextPath + "/swagger-ui/",
    contextPath + "/openapi.yaml",
    contextPath + "/api/auth/config"
));
```

## 访问控制策略变化

| API | 修复前 | 修复后 |
|-----|--------|--------|
| `/api/users/*` | 无需 JWT | 需要 JWT |
| `/api/products/*` | 无需 JWT | 需要 JWT |
| `/api/sys-configs/*` | 无需 JWT | 需要 JWT |
| `/api/menus/*` | 无需 JWT | 需要 JWT |

所有业务 API 现在都强制 JWT 认证。`/api/menus/visible` 和 `/api/menus/tree` 等前端公开菜单端点也已受保护，前端需携带有效 token 请求这些接口。

## 编译验证
✅ BUILD SUCCESS