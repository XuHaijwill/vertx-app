# 审计日志系统

## 概述

审计日志系统记录所有关键业务操作，支持两种写入模式和完整的查询 API。

## 架构

```
请求 → KeycloakAuthHandler（绑定 AuditContext）
     → Service（调用 AuditLogger）
     → AuditRepository（写入 audit_logs 表）
```

## 核心组件

### AuditContext

审计上下文，携带操作者信息：

| 字段 | 说明 | 来源 |
|------|------|------|
| userId | 操作用户 ID | JWT token |
| username | 用户名 | JWT token |
| traceId | 链路追踪 ID | JWT jti / 自动生成 |
| requestId | 请求 ID | X-Request-ID 头 / 自动生成 |
| userIp | 用户 IP | X-Forwarded-For / X-Real-IP / RemoteAddress |
| userAgent | 浏览器标识 | User-Agent 头 |
| serviceName | 服务名 | 固定 "vertx-app" |
| extra | 扩展信息 | 自定义 |

### AuditContextHolder

ThreadLocal 持有 AuditContext，在 KeycloakAuthHandler 中绑定：

```java
// KeycloakAuthHandler.handle() 中
AuditContext auditCtx = new AuditContext()
    .setUserId(userId)
    .setUsername(username)
    .setOrGenerateTraceId(tokenInfo.getString("jti"), reqId)
    .setUserIpFromHeader(fwd, realIp, remoteAddr)
    .setUserAgent(ua)
    .setServiceName("vertx-app");
AuditContextHolder.bind(auditCtx);
```

### AuditAction

审计动作枚举：

| 动作 | 说明 |
|------|------|
| AUDIT_CREATE | 创建 |
| AUDIT_UPDATE | 更新 |
| AUDIT_DELETE | 删除 |
| AUDIT_LOGIN | 登录 |
| AUDIT_LOGOUT | 登出 |
| AUDIT_EXPORT | 导出 |
| AUDIT_IMPORT | 导入 |
| AUDIT_QUERY | 查询 |

## 两种写入模式

### 1. 事务内模式（logInTx）

嵌入当前事务，审计失败则整体回滚。适用于关键操作。

```java
@Override
public Future<Order> createOrder(...) {
    return txTemplate.wrap(tx -> {
        return orderRepo.insertOrder(...)
            .compose(orderId -> audit.logInTx(
                AuditContextHolder.current(),   // 审计上下文
                AuditAction.AUDIT_CREATE,       // 动作
                "orders",                       // 实体类型
                orderId,                        // 实体 ID
                null,                           // 旧值
                orderJson                       // 新值
            ).map(orderId));
    }, 60_000);
}
```

**特点：**
- 审计 INSERT 与业务操作在同一事务
- 任何一步失败 → 整体回滚
- 数据一致性强

### 2. 独立模式（log / logAsync）

独立连接，审计失败不影响主业务。适用于配置变更等非关键操作。

```java
// 同步等待
return repo.update(id, data)
    .compose(updated -> audit.log(
        AuditContextHolder.current(),
        AuditAction.AUDIT_UPDATE,
        "sys_configs", id, oldValue, newValue
    ).map(updated));

// 异步非阻塞（fire-and-forget）
audit.logAsync(AuditAction.AUDIT_UPDATE, "sys_configs", id, oldCfg, newCfg);
```

**特点：**
- 审计写入独立于主事务
- 审计失败不影响业务结果
- `logAsync` 不阻塞，完全异步

### 选择建议

| 场景 | 推荐模式 | 原因 |
|------|---------|------|
| 订单创建 | logInTx | 审计是业务的一部分，必须一致 |
| 支付处理 | logInTx | 金融数据，审计不可丢失 |
| 系统配置变更 | log / logAsync | 配置变更成功即可，审计丢失可接受 |
| 用户登录 | logAsync | 不阻塞登录响应 |

## 数据库表结构

```sql
CREATE TABLE audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    trace_id        VARCHAR(64),
    action          VARCHAR(30) NOT NULL,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       BIGINT,
    old_value       JSONB,          -- 变更前值
    new_value       JSONB,          -- 变更后值
    changed_fields  TEXT[],         -- 变更字段列表
    user_id         BIGINT,
    username        VARCHAR(100),
    user_ip         VARCHAR(50),
    user_agent      VARCHAR(500),
    request_id      VARCHAR(64),
    service_name    VARCHAR(50),
    duration_ms     BIGINT,
    status          VARCHAR(10) DEFAULT 'SUCCESS',
    error_message   TEXT,
    extra           JSONB,
    created_at      TIMESTAMP DEFAULT NOW()
);
```

### 归档表

```sql
CREATE TABLE audit_logs_archive (LIKE audit_logs INCLUDING ALL);
```

## 查询 API

### 搜索审计日志

```
GET /api/audits?action=AUDIT_CREATE&entityType=orders&userId=1&page=1&size=20
```

### 按实体查询

```
GET /api/audits/entity/orders/123
```

### 按用户查询

```
GET /api/audits/user/1?page=1&size=20
```

### 统计

```
GET /api/audits/stats?from=2026-01-01&to=2026-05-01
```

### 归档

```
POST /api/audits/archive
```

将指定时间范围之前的审计日志迁移到 `audit_logs_archive` 表。

## 审计最佳实践

1. **关键操作必须用 logInTx**：订单、支付等涉及金额的操作
2. **非关键操作用 log / logAsync**：配置变更、登录记录等
3. **记录 old_value 和 new_value**：便于追踪变更详情
4. **changed_fields 自动计算**：AuditLogger 内置 diff() 算法
5. **dbAvailable 检查**：调用 `audit.log()` 前确保数据库可用
