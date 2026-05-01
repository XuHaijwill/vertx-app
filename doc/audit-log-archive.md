# 审计日志归档系统使用文档

## 概述

审计日志归档系统实现了日志数据的生命周期管理，将审计日志按时间划分为三个阶段：

```
写入 audit_logs → 满6个月 → archive (迁移到冷表) → 满3年 → purge (彻底删除)
     热数据            温数据                     冷数据
   (高频查询)       (偶尔查，走 view)            (不需要了)
```

| 阶段 | 存储位置 | 说明 |
|------|---------|------|
| 热数据 | `audit_logs` | 最近 6 个月的活跃审计日志 |
| 温数据 | `audit_logs_archive` | 6 个月 ~ 3 年的历史归档 |
| 过期数据 | 已删除 | 超过 3 年的日志被彻底清理 |

---

## 数据库对象

### 表结构

| 对象 | 类型 | 说明 |
|------|------|------|
| `audit_logs` | 表 | 主表，存储活跃审计日志 |
| `audit_logs_archive` | 表 | 归档表，结构与主表相同，额外增加 `archived_at` 列 |
| `audit_logs_all` | 视图 | 统一查询视图，合并主表和归档表数据，通过 `is_archived` 字段区分来源 |

### 索引

```sql
-- 归档表索引
idx_audit_archive_time     -- audit_logs_archive(created_at DESC)  按创建时间查询
idx_audit_archive_archived -- audit_logs_archive(archived_at DESC) 按归档时间查询
```

### 归档表额外字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `archived_at` | TIMESTAMPTZ | 记录何时被归档，默认为归档操作执行时间 |

---

## 核心函数

### 1. `archive_audit_logs` — 日志归档

将主表中超过指定月数的审计日志迁移到归档表。

```sql
SELECT archive_audit_logs(
    p_months_old INTEGER DEFAULT 6,    -- 归档多少个月前的日志
    p_batch_size INTEGER DEFAULT 10000 -- 每批处理多少条
);
```

**参数说明：**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `p_months_old` | INTEGER | 6 | 将多少个月之前的日志视为"旧数据" |
| `p_batch_size` | INTEGER | 10000 | 每批迁移的数量，避免长事务 |

**返回值：** `INTEGER` — 本次归档的总记录数

**执行原理：**
1. 计算截止时间：`当前时间 - N个月`
2. 循环执行，每批从主表 `DELETE ... RETURNING *` 取出旧数据
3. 将取出的数据 `INSERT INTO audit_logs_archive`
4. 每批 `COMMIT`，避免长事务锁表
5. 直到没有更多数据为止

**使用示例：**

```sql
-- 归档 6 个月前的日志（默认）
SELECT archive_audit_logs();

-- 归档 3 个月前的日志，每批 5000 条
SELECT archive_audit_logs(3, 5000);

-- 归档 12 个月前的日志
SELECT archive_audit_logs(12, 10000);
```

---

### 2. `purge_old_audit_logs` — 日志清理

彻底删除归档表中超过指定年数的审计日志。

```sql
SELECT purge_old_audit_logs(
    p_years_old INTEGER DEFAULT 3  -- 删除多少年前的日志
);
```

**参数说明：**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `p_years_old` | INTEGER | 3 | 将多少年之前的归档日志彻底删除 |

**返回值：** `INTEGER` — 本次删除的记录数

**使用示例：**

```sql
-- 删除归档表中 3 年前的日志（默认）
SELECT purge_old_audit_logs();

-- 删除归档表中 5 年前的日志
SELECT purge_old_audit_logs(5);
```

> ⚠️ **注意：** 此操作不可逆，请确认数据不再需要后再执行。

---

## 统一查询视图

### `audit_logs_all`

合并主表和归档表的全量数据，通过 `is_archived` 字段标识数据来源。

```sql
SELECT * FROM audit_logs_all
WHERE entity_type = 'order'
ORDER BY created_at DESC
LIMIT 100;
```

| 字段 | 类型 | 说明 |
|------|------|------|
| （与 audit_logs 相同的 20 个字段） | — | 审计日志标准字段 |
| `is_archived` | BOOLEAN | `FALSE` = 来自主表，`TRUE` = 来自归档表 |

**使用建议：**
- 日常查询审计日志时，优先使用 `audit_logs_all` 视图
- 如确定只查近期数据，可直接查 `audit_logs` 表获得更好的性能
- 视图会扫描两张表，大数据量下建议加上时间范围条件

```sql
-- 推荐：带时间范围的查询
SELECT * FROM audit_logs_all
WHERE created_at >= '2025-01-01'
  AND entity_type = 'order';

-- 不推荐：不带条件的全量查询
SELECT * FROM audit_logs_all;  -- 可能扫描百万级记录
```

---

## 定时任务配置

### 方式一：SchedulerVerticle CLASS 任务（推荐）

通过应用内置的定时任务框架自动执行归档。

在 `scheduled_tasks` 表中插入任务：

```sql
INSERT INTO scheduled_tasks (name, task_type, config, cron, next_run_time, status)
VALUES (
    'archive-audit-logs',      -- 任务名称
    'CLASS',                    -- 任务类型：调用 Java 类
    '{"className": "com.example.task.AuditArchiveTask"}',
    '0 0 4 * * ?',             -- 每天凌晨 4:00 执行
    CURRENT_TIMESTAMP,         -- 下次执行时间
    'ACTIVE'                   -- 启用
);
```

**相关 Java 类：** `AuditArchiveTask.java`

### 方式二：pg_cron 扩展（需 PostgreSQL 支持）

```sql
-- 需要先安装 pg_cron 扩展
CREATE EXTENSION pg_cron;

-- 每天凌晨 4:00 归档
SELECT cron.schedule(
    'archive-audit',
    '0 4 * * *',
    $$SELECT archive_audit_logs(6, 10000)$$
);

-- 每月 1 日凌晨 3:00 清理
SELECT cron.schedule(
    'purge-audit',
    '0 3 1 * *',
    $$SELECT purge_old_audit_logs(3)$$
);
```

### 方式三：SQL 类型任务

通过 SchedulerVerticle 的 SQL 任务类型执行：

```sql
INSERT INTO scheduled_tasks (name, task_type, config, cron, next_run_time, status)
VALUES (
    'archive-audit-logs-sql',
    'SQL',
    '{"sql": "SELECT archive_audit_logs(6, 10000)"}',
    '0 0 4 * * ?',
    CURRENT_TIMESTAMP,
    'ACTIVE'
);
```

---

## 推荐归档策略

| 操作 | 频率 | 建议 |
|------|------|------|
| 归档（archive） | 每天一次 | 凌晨低峰期执行，默认保留 6 个月 |
| 清理（purge） | 每月一次 | 月初执行，默认保留 3 年 |
| 备份归档表 | 每周一次 | `pg_dump -t audit_logs_archive` |

---

## 常见问题

### Q: 归档操作会影响业务性能吗？

归档使用分批处理（默认每批 10000 条），每批独立提交事务。单批执行时间通常在秒级，对业务影响极小。建议在低峰期（如凌晨）执行。

### Q: 归档后的数据还能查到吗？

可以。通过 `audit_logs_all` 视图可以同时查询主表和归档表的数据。`is_archived = TRUE` 标识来自归档表。

### Q: 误删了归档数据怎么办？

`purge_old_audit_logs` 不可逆。建议在执行清理前先备份归档表：

```sql
-- 备份归档表
pg_dump -h localhost -U postgres -d mydb -t audit_logs_archive > audit_archive_backup.sql
```

### Q: 如何查看当前归档状态？

```sql
-- 主表记录数
SELECT COUNT(*) AS active_count FROM audit_logs;

-- 归档表记录数
SELECT COUNT(*) AS archived_count FROM audit_logs_archive;

-- 最近一次归档时间
SELECT MAX(archived_at) AS last_archive_time FROM audit_logs_archive;

-- 各月数据分布
SELECT
    DATE_TRUNC('month', created_at) AS month,
    COUNT(*) FILTER (WHERE NOT is_archived) AS active,
    COUNT(*) FILTER (WHERE is_archived) AS archived
FROM audit_logs_all
GROUP BY month
ORDER BY month DESC
LIMIT 12;
```

### Q: archive_audit_logs 的 SELECT 返回值是什么？

返回本次迁移的总记录数（INTEGER）。例如返回 `15234` 表示有 15234 条日志被从主表迁移到归档表。

---

## 相关文件

| 文件 | 说明 |
|------|------|
| `src/main/resources/db/migration/V8__audit_logs.sql` | 主表迁移 |
| `src/main/resources/db/migration/V9__audit_logs_archive.sql` | 归档表、函数、视图 |
| `src/main/java/com/example/task/AuditArchiveTask.java` | Java 定时归档任务 |
| `src/main/java/com/example/service/AuditLogger.java` | 审计日志记录服务 |
| `src/main/java/com/example/api/AuditApi.java` | 审计日志 REST API |
