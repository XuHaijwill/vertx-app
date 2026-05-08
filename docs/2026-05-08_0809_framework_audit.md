# vertx-app 框架代码审查报告

**时间：** 2026-05-08
**审查范围：** `com.example` 核心框架层（API/Service/Repository/Entity/Auth/DB）

---

## ✅ 框架基础设施（质量高）

| 组件 | 状态 | 说明 |
|------|------|------|
| `BaseApi` | ✅ | 完整：ok/created/fail/badRequest/notFound + respond 系列 + parseInt/queryStr/bodyJson |
| `BaseServiceImpl` | ✅ | 模板模式清晰，dbAvailable 短路逻辑统一 |
| `TransactionContext` | ✅ | 操作计数、超时检查、rollbackOnly 机制完善 |
| `TxContextHolder` | ✅ | Vert.x Context-local 绑定，支持嵌套检测 |
| `TransactionTemplate` | ✅ | `wrap()` 声明式事务引擎，绑定/解绑 + 解绑安全网完善 |
| `DatabaseVerticle` | ✅ | 连接池管理、withTransaction、迁移、演示模式支持 |
| `AuditLogger` | ✅ | 双模式（logInTx/log），diff 智能比较，logAsync 不阻塞 |
| `AuditContext` | ✅ | 含 extra/traceId/userIp 提取/截断逻辑完整 |
| `PageResult` | ✅ | 反射 toJson 支持实体列表 |
| `ApiResponse` | ✅ | putExtra/putExtras 链式扩展，configure() 动态字段映射 |
| `KeycloakAuthHandler` | ✅ | Ehcache 缓存 + JWKS 预加载 + 回退降级逻辑 |

---

## ❌ 问题清单

### P0 — 功能缺失

#### 1. `KeycloakAuthHandler` 未绑定 AuditContext

**文件：** `src/main/java/com/example/auth/KeycloakAuthHandler.java`

**问题：** 鉴权成功后创建了 `AuthUser` 放入 RoutingContext，但从未创建 `AuditContext` 并调用 `AuditContextHolder.bind(ctx)`。导致所有业务操作的 `AuditContextHolder.current()` 返回 null，审计日志中 userId/username/traceId/userIp 全部为空。

**修复方向：**
```java
// 在 handle() 的 JWT 验证成功分支，添加：
AuditContext auditCtx = new AuditContext()
    .setUserId(userId)
    .setUsername(username)
    .setTraceId(tokenInfo.getString("jti"))   // JWT ID
    .setRequestId(ctx.get("requestId"))
    .setUserIpFromHeader(
        ctx.request().getHeader("X-Forwarded-For"),
        ctx.request().getHeader("X-Real-IP"),
        ctx.request().remoteAddress() != null ? ctx.request().remoteAddress().host() : null)
    .setUserAgent(ctx.request().getHeader("User-Agent"))
    .setServiceName("vertx-app");
AuditContextHolder.bind(auditCtx);
```

同时在 Router 响应结束时需添加解绑回调（可在 MainVerticle 的 addGlobalHandlers 中统一添加，或在 KeycloakAuthHandler 的 finally 中处理）。

---

#### 2. `OrderServiceImpl` / `PaymentServiceImpl` — `@Transactional` 无实际拦截器

**文件：** `src/main/java/com/example/service/impl/OrderServiceImpl.java`

**问题：** `@Transactional` 注解存在但无 AOP/拦截器实现，方法体不会自动开启事务。虽然 OrderRepository 的方法通过 `TxContextHolder.current()` 实现了事务路由（auto-route 模式），但如果 Service 层直接调用 repo 的 pool 方法（不用 auto-route），就会脱离事务。

`createOrder` 中 `insertItemsSequence` / `deductStockSequence` 调用的是 `productRepo.findByIdForUpdate`（auto-route），但 `insertOrder` 调用的是 `orderRepo.insertOrder`（也是 auto-route）。逻辑上这个 service 是依赖 repo 的 auto-route 事务检测工作的，但 `cancelOrder` 中 `orderRepo.updateStatus(orderId, "cancelled")` 是一个重载方法（直接 pool 版本？）——需确认 `OrderRepository.updateStatus(Long, String)` 不依赖 TxContextHolder 才安全。

**修复方向：**
方案 A（推荐）：在 `OrderServiceImpl` 和 `PaymentServiceImpl` 的 `@Transactional` 方法开头显式调用 `TransactionTemplate.wrap()`：
```java
TransactionTemplate txTemplate = new TransactionTemplate(vertx);
return txTemplate.wrap(tx -> {
    // 所有 repo 操作传入 tx 参数
});
```
方案 B：添加 `@Transactional` 的运行时拦截器（需要引入 AspectJ 或自定义切面）。

---

### P1 — 运行时风险

#### 3. `ScheduledTaskServiceImpl` — 全部方法缺失 dbAvailable 短路

**文件：** `src/main/java/com/example/service/impl/ScheduledTaskServiceImpl.java`

**问题：** 所有方法（findAll/findById/create/update/delete 等）直接调用 `repo.*`，完全未检查 `dbAvailable`。如果数据库未连接，repo 会返回 failed Future，但与 BaseServiceImpl 模式不一致，可能导致未预期的空指针或超时。

```java
// 修复示例：
@Override
public Future<List<ScheduledTask>> findAll() {
    if (!dbAvailable) return failIfUnavailable();
    return repo.findAll();
}
```

---

#### 4. `OrderServiceImpl.cancelOrder` — updateStatus 调用两次

**文件：** `src/main/java/com/example/service/impl/OrderServiceImpl.java`（约第 73-75 行）

**问题：** `cancelOrder` 方法链中，`restoreStockSequence` 后调用了 `orderRepo.updateStatus(orderId, "cancelled")`，但在 `restoreStockSequence` 的 compose chain 中可能也已设置过状态。需确认是否重复。

**检查：** 搜索 `updateStatus` 在 `cancelOrder` 中的调用次数，逻辑上应该在 restore 成功后只调用一次。

---

### P2 — 编译/编码问题

#### 5. `AuditApi` — 多处 searchCount 参数个数不匹配

**文件：** `src/main/java/com/example/api/AuditApi.java`

`AuditRepository.searchCount` 方法签名：
```java
Future<Long> searchCount(String entityType, String entityId, Long userId,
                          String action, String status, String username,
                          String from, String to)
```
共 8 个参数。但 `AuditApi.search()` 调用时传了 9 个参数（多了 int page）：
```java
auditRepo.search(..., page, size)  // 传了 page 参数
```
注意 `search` 和 `searchCount` 参数列表不同，search 有 page/size，searchCount 没有。但如果 `AuditApi` 调用的 `searchCount` 签名与 repo 不匹配，编译应报错。需确认 repo 接口签名是否实际包含 page 参数。

**另需检查：** `summary()` 方法中三次 `searchCount` 调用缺少 `entityId` 参数位置（第二个参数），需对照 repo 接口确认参数对齐。

---

#### 6. `ProductServiceImpl.batchUpdate` — 循环错误计数器不生效

**文件：** `src/main/java/com/example/service/impl/ProductServiceImpl.java`

```java
int[] failed = {0};
Future<Void> chain = Future.succeededFuture();
for (Product p : products) {
    chain = chain.compose(v -> repo.update(p.getId(), p)
        .onSuccess(u -> { if (u != null) updated.add(u); })
        .onFailure(e -> failed[0]++)   // ← 这个不会生效！
        .mapEmpty());
}
```

问题：`onFailure` 在 `compose` 外部，不会被 `compose` 捕获。compose 链遇到失败 future 会直接跳到 onFailure handler，但这里 `onFailure` 是在 compose 之后附加的，compose 无法感知它。更严重的是 `mapEmpty()` 始终返回成功，即使 update 失败也会继续到下一个，无法真正计数失败。

**修复方向：** 用正确的链式写法：
```java
Future<Void> chain = Future.succeededFuture();
for (Product p : products) {
    final Product fp = p;
    chain = chain.compose(v -> repo.update(fp.getId(), fp)
        .map(u -> { if (u != null) updated.add(u); return null; })
        .recover(e -> { failed[0]++; return Future.succeededFuture(); }));
}
```

---

### P3 — 潜在逻辑问题

#### 7. `SysDictTypeServiceImpl` — `doCreate`/`doUpdate` 未检查 dbAvailable

**文件：** `src/main/java/com/example/service/impl/SysDictTypeServiceImpl.java`

`doCreate` 和 `doUpdate` 内部调用 `audit.log()`，如果 dbAvailable=false 则这些调用可能在 repo 操作返回后执行。实际影响轻微（audit 已在 BaseServiceImpl 构造函数中初始化），但与其他 ServiceImpl 模式不一致。

---

#### 8. `PaymentServiceImpl.lockPaymentForUpdate` / `findPaymentEnriched` — TxContext 为 null 时抛异常

**文件：** `src/main/java/com/example/service/impl/PaymentServiceImpl.java`

如果这些方法在 `@Transactional` 方法内被调用（通过 TxContextHolder），则正常。但如果被非事务方法调用，会抛出 `IllegalStateException`。这是设计决策，但应该用 BusinessException 包装并给出友好信息。

---

#### 9. `BatchApi` — 无权限控制

**文件：** `src/main/java/com/example/api/BatchApi.java`

批量操作（create/update/delete）无 `@PreAuthorize` 或 `RequirePermission` 注解，如果 Keycloak 启用但 whitelist 不过滤 `/api/products/batch`，则任何认证用户都能批量操作。

---

### P4 — 清理建议

#### 10. 移除未使用的 import

| 文件 | 清理内容 |
|------|---------|
| `OrderServiceImpl.java` | `import io.vertx.core.json.JsonArray`（未使用） |
| `PaymentServiceImpl.java` | 多余空白行 |
| `SysDictDataRepository.java` | `import java.util.stream.Collectors`（未使用） |
| `OrderRepository.java` | `import java.util.stream.StreamSupport`（未使用） |

#### 11. `InventoryTransactionRepository` 中的命名一致性

`invTxRepo.recordDeduction` / `recordRestoration` 需确认与数据库存储过程/SP 匹配，名称硬编码容易出错。

---

## 修复优先级建议

| 优先级 | 问题 | 预计工时 |
|--------|------|---------|
| P0-1 | AuditContext 绑定缺失 | ~30 分钟 |
| P0-2 | @Transactional 实际拦截 | ~1 小时（需设计决策） |
| P1-1 | ScheduledTaskServiceImpl dbAvailable | ~20 分钟 |
| P1-2 | batchUpdate 链式 bug | ~15 分钟 |
| P2 | searchCount 参数对齐 | ~10 分钟 |

---

## 框架设计亮点（值得保留）

- **TxContextHolder 自动路由**：Repository 自动检测活跃事务，无须 service 层显式传参
- **dbAvailable 短路模式**：所有 ServiceImpl 统一处理 demo mode
- **AuditLogger 双模式**：logInTx 确保关键操作一致性，logAsync 不阻塞业务
- **BaseServiceImpl 模板**：`Function<Vertx, R>` 工厂模式减少样板代码
- **TransactionTemplate withConnection**：独立操作的连接管理简洁