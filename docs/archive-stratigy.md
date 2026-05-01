实现摘要
归档策略
表	保留期限	说明
audit_logs	最近 1 年	活跃数据，高频查询
audit_logs_archive	1 年以上	历史归档，低频访问
自动归档	> 6 个月	定期迁移到 archive
自动清理	> 3 年	删除过旧归档
数据库层 (V9__audit_logs_archive.sql)
sql


-- 归档函数
SELECT archive_audit_logs(6, 10000);  -- 迁移 > 6 个月的日志

-- 清理函数
SELECT purge_old_audit_logs(3);       -- 删除 > 3 年的归档

-- 统一视图
SELECT * FROM audit_logs_all;         -- 主表 + 归档表
API 端点
bash


# 查询归档统计
GET /api/audit-logs/stats/archive

# 手动触发归档
POST /api/audit-logs/archive
{"monthsOld": 6, "batchSize": 10000}

定时任务配置


-- 每天凌晨 4:00 自动归档
INSERT INTO scheduled_tasks (name, task_type, config, cron, status)
VALUES ('archive-audit-logs', 'CLASS',
'{"class": "com.example.tasks.AuditArchiveTask", "method": "archive", "async": true}',
'0 0 4 * * ?', 'ACTIVE');