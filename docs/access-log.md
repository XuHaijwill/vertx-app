# Access Log 访问日志

## 概述

Access Log 模块自动记录所有 `/api/*` HTTP 请求的访问行为，提供查询、统计和自动清理能力。

**记录内容：** 请求方法/路径、响应状态码/耗时、用户 IP/Agent、认证用户、错误信息等。

**核心文件：**

| 层次 | 文件 |
|------|------|
| 数据库 | `db/migration/V13__access_log.sql` |
| 实体 | `entity/AccessLog.java` |
| 数据访问 | `repository/AccessLogRepository.java` |
| 业务逻辑 | `service/impl/AccessLogServiceImpl.java` |
| REST API | `api/AccessLogApi.java` |
| 请求拦截 | `handlers/AccessLogHandler.java` |
| 定时清理 | `tasks/AccessLogCleanupTask.java` |

---

## API 端点

所有端点需要登录认证（匿名用户只有 traceId/requestId/ip/agent/method/path 字段有值）。

### 搜索 + 分页

```
GET /api/access-logs
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `userId` | integer | 按用户 ID 筛选 |
| `username` | string | 用户名模糊匹配 |
| `method` | string | HTTP 方法，如 `GET` `POST` |
| `path` | string | 路径模糊匹配 |
| `statusCode` | integer | HTTP 状态码 |
| `userIp` | string | 客户端 IP 模糊匹配 |
| `from` | string | 起始日期 `YYYY-MM-DD` |
| `to` | string | 结束日期 `YYYY-MM-DD` |
| `page` | integer | 页码（默认 1） |
| `size` | integer | 每页条数（默认 20，最大 200） |

**响应示例：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      {
        "id": 42,
        "traceId": "abc123",
        "userId": 1,
        "username": "admin",
        "method": "POST",
        "path": "/api/orders",
        "queryString": null,
        "statusCode": 200,
        "responseTime": 85,
        "userIp": "192.168.1.100",
        "userAgent": "Mozilla/5.0...",
        "createdAt": "2026-05-08T10:23:45+08:00"
      }
    ],
    "total": 1523,
    "page": 1,
    "size": 20,
    "totalPages": 77
  }
}
```

---

### 按 ID 查询

```
GET /api/access-logs/:id
```

**响应示例：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 42,
    "traceId": "abc123",
    "userId": 1,
    "username": "admin",
    "method": "POST",
    "path": "/api/orders",
    "statusCode": 200,
    "responseTime": 85,
    "userIp": "192.168.1.100",
    "userAgent": "Mozilla/5.0...",
    "requestId": "req-456",
    "errorMessage": null,
    "createdAt": "2026-05-08T10:23:45+08:00"
  }
}
```

---

### 按用户查询

```
GET /api/access-logs/user/:userId
```

| 参数 | 说明 |
|------|------|
| `userId` | 用户 ID（路径参数） |
| `limit` | 返回条数（默认 50，最大 200） |

**响应示例：**

```json
{
  "code": 0,
  "message": "success",
  "data": [
    { "id": 42, "method": "POST", "path": "/api/orders", "statusCode": 200, "createdAt": "2026-05-08T10:23:45+08:00" },
    { "id": 41, "method": "GET", "path": "/api/products", "statusCode": 200, "createdAt": "2026-05-08T10:22:00+08:00" }
  ]
}
```

---

### 统计接口

```
GET /api/access-logs/stats/summary?days=7
GET /api/access-logs/stats/paths?days=7&limit=10
GET /api/access-logs/stats/status?days=7
```

| 接口 | 说明 | 特有参数 |
|------|------|----------|
| `/stats/summary` | 概览统计 | `days`（默认 7，范围 1-90） |
| `/stats/paths` | Top N 热点路径 | `days`、`limit`（默认 10，最大 100） |
| `/stats/status` | 状态码分布 | `days`（默认 7） |

**stats/summary 响应示例：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "total": 12580,
    "successCount": 12100,
    "errorCount": 480,
    "serverErrorCount": 12,
    "avgResponseTime": 67,
    "maxResponseTime": 3420,
    "uniqueUsers": 23,
    "uniquePaths": 87,
    "days": 7
  }
}
```

**stats/paths 响应示例：**

```json
{
  "code": 0,
  "message": "success",
  "data": [
    { "path": "/api/products", "count": 4200, "avgResponseTime": 45 },
    { "path": "/api/orders", "count": 2800, "avgResponseTime": 110 },
    { "path": "/api/users/me", "count": 1500, "avgResponseTime": 22 }
  ]
}
```

**stats/status 响应示例：**

```json
{
  "code": 0,
  "message": "success",
  "data": [
    { "statusCode": 200, "count": 11000 },
    { "statusCode": 401, "count": 280 },
    { "statusCode": 404, "count": 120 },
    { "statusCode": 500, "count": 12 }
  ]
}
```

---

### 保留天数管理

```
GET /api/access-logs/retention        # 获取当前保留天数
PUT /api/access-logs/retention        # 修改保留天数
```

**GET 响应：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "retentionDays": 90,
    "configKey": "sys.access-log.retentionDays"
  }
}
```

**PUT 请求体：**

```json
{ "retentionDays": 180 }
```

- `retentionDays = 90`：保留 90 天后自动清理（默认）
- `retentionDays = 0`：永不过期，禁用自动清理
- `retentionDays < 0`：返回 400 错误

**PUT 响应：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "retentionDays": 180,
    "message": "Logs older than 180 days will be auto-deleted"
  }
}
```

---

### 手动清理

```
DELETE /api/access-logs/cleanup
```

| 参数 | 说明 |
|------|------|
| `retentionDays` | 可选，指定清理天数；不传则使用 sys_config 配置值 |

**响应示例：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "deletedCount": 3421,
    "message": "3421 expired log records deleted"
  }
}
```

---

## 数据表结构

```sql
CREATE TABLE access_log (
    id              BIGSERIAL PRIMARY KEY,
    trace_id        VARCHAR(64),           -- 请求追踪 ID（JWT jti 或生成）
    user_id         BIGINT,                -- 操作用户 ID（未登录为 NULL）
    username        VARCHAR(128),          -- 用户名（冗余存储）
    method          VARCHAR(10) NOT NULL,  -- GET / POST / PUT / DELETE
    path            VARCHAR(500) NOT NULL, -- 请求路径
    query_string    VARCHAR(1000),         -- 查询参数
    status_code     INTEGER NOT NULL,      -- HTTP 响应状态码
    response_time   INTEGER,              -- 响应时间（ms）
    user_ip         VARCHAR(45),           -- 客户端 IP
    user_agent      VARCHAR(512),          -- User-Agent
    request_id      VARCHAR(64),          -- HTTP 请求 ID
    error_message   VARCHAR(512),         -- 5xx 时记录的错误消息
    extra           JSONB,                 -- 扩展字段
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

**索引：**

| 索引名 | 字段 | 用途 |
|--------|------|------|
| `idx_access_log_time` | `created_at DESC` | 列表查询 + 定时清理 |
| `idx_access_log_user` | `user_id, created_at DESC` | 用户访问历史 |
| `idx_access_log_path` | `path, created_at DESC` | 热点接口分析 |
| `idx_access_log_status` | `status_code, created_at DESC` | 错误分析 |
| `idx_access_log_trace` | `trace_id` | 分布式追踪 |
| `idx_access_log_method_path` | `method, path` | 组合条件查询 |

---

## 保留天数配置

### 配置项

配置存储在 `sys_config` 表：

| 字段 | 值 |
|------|-----|
| `config_key` | `sys.access-log.retentionDays` |
| `config_value`（默认） | `90` |
| `config_name` | `Access log retention days` |
| 说明 | 设为 `0` 禁用自动清理 |

### 修改方式

**方式 1：API（推荐）**

```
PUT /api/access-logs/retention
{"retentionDays": 180}
```

**方式 2：直接改数据库**

```sql
UPDATE sys_config
SET config_value = '180'
WHERE config_key = 'sys.access-log.retentionDays';
```

修改后**无需重启**，下次定时清理任务执行时自动生效。

---

## 定时清理任务

### 启用方式

在 `scheduled_tasks` 表插入任务：

```sql
INSERT INTO scheduled_tasks (name, task_type, config, cron, next_run_time, status)
VALUES (
    'cleanup-access-log',
    'CLASS',
    '{"class": "com.example.tasks.AccessLogCleanupTask", "method": "cleanup", "async": true, "params": {}}',
    '0 0 3 * * ?',  -- 每天凌晨 3:00 执行
    CURRENT_TIMESTAMP,
    'ACTIVE'
);
```

### 参数说明

| 参数 | 说明 |
|------|------|
| `retentionDays` | 可选，固定清理天数（覆盖 sys_config） |

### 日志输出

```
[AccessLogCleanup] Starting cleanup task
[AccessLogCleanup] Deleting logs older than 90 days
[AccessLogCleanup] Deleted 3421 access log records older than 90 days
[AccessLogCleanup] Auto-cleanup disabled (retentionDays=0)
```

---

## 数据字段说明

| 字段 | 说明 |
|------|------|
| `trace_id` | 分布式追踪 ID，同一次请求的所有日志共享。取自 JWT `jti` claim，若无则生成 UUID |
| `request_id` | HTTP 请求级别唯一 ID，取自 `X-Request-ID` 请求头 |
| `user_id` / `username` | 从 `KeycloakAuthHandler` 注入的认证用户信息，未登录则为 `NULL` |
| `user_ip` | 优先级：`X-Forwarded-For` 第一段 > `X-Real-IP` > 远程地址 |
| `error_message` | 仅记录 5xx 错误，截断至 512 字符 |
| `response_time` | 从 `AccessLogHandler` 进入（`ctx.next()`）到响应写入完成的时间（ms） |

---

## 异步写入机制

`AccessLogHandler` 使用 **独立连接异步写入**，不影响业务请求性能：

1. `ctx.response().bodyEndHandler()` - 在 HTTP 响应写完后触发，不阻塞
2. `repo.insert(log)` - fire-and-forget，失败只打 DEBUG 日志，不抛异常
3. 若数据库不可用，写入静默跳过（`dbAvailable` 检查）

---

## 权限要求

| 端点 | 所需权限 |
|------|----------|
| 所有 `GET /api/access-logs/*` | 登录即可（`isAuthenticated()`） |
| `PUT /api/access-logs/retention` | 登录即可 |
| `DELETE /api/access-logs/cleanup` | 登录即可 |