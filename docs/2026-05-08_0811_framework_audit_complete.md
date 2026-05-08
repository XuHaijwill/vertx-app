# 框架代码审查与完善 — 2026-05-08

## 审查范围
vertx-app `com.example` 核心框架层（60+ 文件）

---

## 已修复问题

### P0-1: AuditContext 未绑定（最关键）
**文件：** `KeycloakAuthHandler.java`

鉴权成功后未绑定 AuditContext，导致审计日志中 userId/username/traceId/userIp 全为空。

**修复：** JWT 验证成功后添加 AuditContext 绑定，从请求头中提取 X-Forwarded-For/X-Real-IP/RemoteAddress/User-Agent，并设置 traceId（JWT jti claim）、requestId。

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

---

### P1-1: ScheduledTaskServiceImpl — 全部方法缺失 dbAvailable 短路
**文件：** `ScheduledTaskServiceImpl.java`

`findAll/findById/findByName/findByStatus/findByTaskType/findPaginated/search/searchCount/count/create/update/delete/existsByName/existsByNameAndNotId` 全部直接调用 repo，无 dbAvailable 短路。

**修复：** 所有读方法添加 `failIfUnavailable()` / `failIfUnavailableNull()` 短路；写方法添加 demo mode fallback（生成 ID 等）。

⚠️ 修复中遇到 Java 泛型类型推断问题（`Future.succeededFuture(new PageResult<>(...))` 中 `PageResult<?>` 无法推断为 `List<ScheduledTask>`），最终改用 `failIfUnavailable()` 统一短路返回空列表。

---

### P1-2: ProductServiceImpl.batchUpdate — 链式错误计数器 bug
**文件：** `ProductServiceImpl.java`

原代码：
```java
chain = chain.compose(v -> repo.update(...)
    .onSuccess(u -> { ... })
    .onFailure(e -> failed[0]++)  // ← onFailure 不被 compose 捕获！
    .mapEmpty());                 // ← 无论成功失败都返回空，导致链不中断
```

**修复：** 使用 `.recover()` 在 compose 链中正确捕获失败：
```java
chain = chain.compose(v -> repo.update(fp.getId(), fp)
    .map(u -> { if (u != null) updated.add(u); return null; })
    .recover(e -> { failed[0]++; return Future.succeededFuture(); }));
```

---

## 已识别但未修复的问题（记录）

| 优先级 | 问题 | 说明 |
|--------|------|------|
| P0-2 | @Transactional 无实际拦截器 | OrderServiceImpl/PaymentServiceImpl 的 @Transactional 注解存在但无 AOP 实现；依赖 repo 的 auto-route 模式工作，需架构决策（TransactionTemplate.wrap 或 AOP） |
| P2 | AuditApi.searchCount 参数对齐 | summary() 调用 searchCount 时少了 entityId 参数位置，需对照 repo 接口确认 |
| P3 | SysDictTypeServiceImpl — doCreate/doUpdate 未保护 | dbAvailable=false 时 audit.log 可能失败（轻微） |
| P3 | PaymentServiceImpl lockPaymentForUpdate — 应抛 BusinessException | 当前抛 IllegalStateException，消息不够友好 |
| P4 | BatchApi 无权限控制 | 批量操作无 RequirePermission 注解 |

---

## 编译状态
✅ `mvn compile -DskipTests` → BUILD SUCCESS

## 框架亮点（设计质量高）
- TxContextHolder 自动路由（Repository 无须显式传参）
- dbAvailable 短路模式（所有 BaseServiceImpl 子类统一）
- AuditLogger 双模式（logInTx 强一致性 / logAsync 不阻塞）
- BaseServiceImpl 模板（Function<Vertx, R> 工厂模式）
- KeycloakAuthHandler（Ehcache 缓存 + JWKS 预加载 + 降级策略）