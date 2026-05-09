# Vert.x App 文档

> 基于 Vert.x 5 + PostgreSQL 的后端应用

## 📖 文档目录

| 文档 | 说明 |
|------|------|
| [架构概览](architecture.md) | 项目结构、分层架构、技术栈、设计决策 |
| [开发指南](development-guide.md) | 新增 API 模块完整流程、编码规范 |
| [事务管理](transaction-guide.md) | TransactionTemplate 使用、Repository 自动感知事务 |
| [审计日志](audit-logging.md) | 双模式审计（logInTx / logAsync）、AuditContext |
| [认证与权限](authentication.md) | Keycloak JWT、Token 缓存、RequirePermission |
| [Repository 模式](repository-pattern.md) | 查询模式、实体映射、分页、动态 SQL |
| [API 参考](api-reference.md) | 全部 REST 端点、请求/响应格式 |
| [部署指南](deployment.md) | 本地开发、Docker、K8s、Keycloak 配置 |

## 📁 历史记录

| 文档 | 说明 |
|------|------|
| [事务架构分析](transaction-architecture-analysis.md) | 事务系统深度分析（V1） |
| [审计归档策略](archive-stratigy.md) | 审计日志归档方案 |
| [P0 ErrorHandler 修复](2026-05-07_2222_P0_ErrorHandler_fix.md) | 全局错误处理器修复 |
| [P1 Auth 白名单](2026-05-07_2227_P1_AuthWhitelist.md) | 认证白名单清理 |
| [P1 BaseServiceImpl](2026-05-07_2231_P1_BaseServiceImpl.md) | 服务基类抽取 |
| [框架审计](2026-05-08_0809_framework_audit.md) | 全框架代码审查报告 |
| [审计完成](2026-05-08_0811_framework_audit_complete.md) | 审查修复完成报告 |
| [修复汇总](2026-05-08_0833_fix_report.md) | 编译修复+框架修复汇总 |

## 快速开始

```bash
# 1. 启动 PostgreSQL
docker run -d --name vertx-pg \
  -e POSTGRES_DB=vertx_app \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 postgres:16

# 2. 编译
mvn clean compile -DskipTests

# 3. 运行
mvn exec:java -Dexec.mainClass="com.example.App"

# 4. 验证
curl http://localhost:8888/health
```
