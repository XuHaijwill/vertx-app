# 认证与权限

## 概述

基于 Keycloak JWT 的认证系统，支持 Token 缓存和细粒度权限控制。

## 认证流程

```
客户端请求（带 Bearer Token）
  → KeycloakAuthHandler.handle()
    → 检查白名单路径
    → 查询 TokenCacheManager 缓存
      → 命中 → 直接设置 userPrincipal/userRoles
      → 未命中 → JWTAuth.authenticate()
        → JWKS 公钥验证签名
        → 验证成功 → 缓存 Token
        → 绑定 AuditContext
    → ctx.next()
  → RequirePermission 中间件（可选）
  → API Handler
```

## KeycloakAuthHandler

### 白名单路径

以下路径跳过认证：

| 路径 | 说明 |
|------|------|
| `/health` | 健康检查 |
| `/docs` | Swagger 文档 |
| `/swagger-ui/` | Swagger UI 静态资源 |
| `/openapi.yaml` | OpenAPI 规范 |
| `/api/auth/config` | 认证配置（公钥端点） |

> 所有 `/api/*` 路径默认需要认证。需要公开访问的端点应使用 `RequirePermission.publicAccess()` 而不是加入白名单。

### JWT 验证

1. 从 `Authorization: Bearer <token>` 提取 Token
2. 使用 Keycloak JWKS 端点获取公钥
3. 验证签名 + 过期时间
4. 提取用户信息（principal、roles、username）

### Token 缓存

使用 Ehcache 缓存已验证的 Token，避免重复 JWKS 验证：

| 配置 | 说明 |
|------|------|
| Key | SHA-256(token).substring(0, 32) |
| Value | TokenInfo{principal, roles, username, expiration} |
| TTL | JWT exp - 当前时间（精确到秒） |

### AuditContext 绑定

JWT 验证成功后自动绑定审计上下文：

```java
AuditContext auditCtx = new AuditContext()
    .setUserId(userId)
    .setUsername(username)
    .setOrGenerateTraceId(tokenInfo.getString("jti"), reqId)
    .setRequestId(reqId)
    .setUserIpFromHeader(fwd, realIp, remoteAddr)
    .setUserAgent(ua)
    .setServiceName("vertx-app");
AuditContextHolder.bind(auditCtx);
```

## 权限控制

### RequirePermission

基于注解的权限中间件，用于 API 路由：

```java
// 单个权限
router.post("/api/products")
    .handler(RequirePermission.of("system:product:add"))
    .handler(this::createProduct);

// 多个权限（任一满足）
router.put("/api/users/:id")
    .handler(RequirePermission.ofAny("system:user:edit", "system:user:admin"))
    .handler(this::updateUser);

// 公开访问（无需权限，但仍需认证）
router.get("/api/menus/tree")
    .handler(RequirePermission.publicAccess())
    .handler(this::menuTree);
```

### 权限格式

遵循 RuoYi 权限命名规范：`模块:业务:操作`

| 示例 | 说明 |
|------|------|
| `system:user:query` | 查询用户 |
| `system:user:add` | 新增用户 |
| `system:user:edit` | 编辑用户 |
| `system:user:remove` | 删除用户 |
| `system:product:add` | 新增商品 |
| `system:order:edit` | 编辑订单 |

### 角色提取

支持两层角色：

| 层级 | JWT Claim | 说明 |
|------|-----------|------|
| Realm-level | `realm_access.roles` | 全局角色 |
| Client-level | `resource_access.<clientId>.roles` | 客户端角色 |

## AuthConfig 配置

通过 Vert.x config 传入：

```json
{
  "auth.enabled": true,
  "auth.keycloak.issuer": "https://keycloak.example.com/realms/myrealm",
  "auth.keycloak.clientId": "vertx-app",
  "auth.keycloak.jwksPath": "/protocol/openid-connect/certs"
}
```

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `auth.enabled` | `false` | 是否启用认证 |
| `auth.keycloak.issuer` | - | Keycloak Realm URL |
| `auth.keycloak.clientId` | - | Keycloak Client ID |
| `auth.keycloak.jwksPath` | `/protocol/openid-connect/certs` | JWKS 端点路径 |

## API 端点

### 获取认证配置

```
GET /api/auth/config
```

返回 Keycloak 公钥信息（无需认证）。

### 获取当前用户信息

```
GET /api/auth/me
```

返回当前认证用户的 principal、roles、permissions。

## 安全注意事项

1. **生产环境必须启用认证**：`auth.enabled = true`
2. **使用 HTTPS**：JWT Token 明文传输有被窃取风险
3. **白名单最小化**：只开放真正需要公开的端点
4. **权限最小化**：每个端点只授予必要权限
5. **Token 过期时间**：建议 Keycloak 设置合理的 access token lifespan
