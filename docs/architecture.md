# 架构概览

Vert.x 应用采用分层架构，基于 Vert.x 5 + PostgreSQL 构建。

## 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| Web 框架 | Vert.x 5 Web | 异步非阻塞 HTTP 服务 |
| 数据库 | PostgreSQL | 关系型存储 |
| 连接池 | vertx-pgclient | 响应式 PostgreSQL 客户端 |
| 数据库迁移 | Flyway | 版本化 Schema 管理 |
| 认证 | Keycloak + JWT | JWKS 公钥验证 |
| 缓存 | Ehcache 3 | Token 本地缓存 |
| JSON | Jackson + JavaTimeModule | Java 8 日期时间支持 |
| 日志 | SLF4J + Logback | 结构化日志 |

## 项目结构

```
src/main/java/com/example/
├── App.java                        # 应用入口
├── MainVerticle.java               # HTTP 服务器 + 路由注册
│
├── api/                            # REST API 层
│   ├── BaseApi.java                # 响应工具 + 参数解析
│   ├── HealthApi.java              # 健康检查
│   ├── AuthApi.java                # 认证端点
│   ├── UserApi.java                # 用户 CRUD + 批量
│   ├── ProductApi.java             # 商品 CRUD + 批量
│   ├── OrderApi.java               # 订单管理
│   ├── PaymentApi.java             # 支付管理
│   ├── SysConfigApi.java           # 系统配置
│   ├── SysDictTypeApi.java         # 字典类型
│   ├── SysDictDataApi.java         # 字典数据
│   ├── SysMenuApi.java             # 菜单管理（含权限）
│   ├── ScheduledTaskApi.java       # 定时任务
│   ├── AuditApi.java               # 审计日志查询
│   ├── BatchApi.java               # 批量操作
│   └── DocsApi.java                # Swagger 文档
│
├── auth/                           # 认证与权限
│   ├── AuthConfig.java             # Keycloak 配置
│   ├── AuthUser.java               # 认证用户模型
│   ├── AuthUtils.java              # 认证工具
│   ├── KeycloakAuthHandler.java    # JWT 验证 + AuditContext 绑定
│   ├── RequirePermission.java      # 权限注解 + 中间件
│   └── PermissionService.java      # 权限校验服务
│
├── cache/
│   └── TokenCacheManager.java      # Ehcache Token 缓存
│
├── core/                           # 核心基础设施
│   ├── ApiResponse.java            # 统一响应封装
│   ├── PageResult.java             # 分页结果包装
│   ├── BusinessException.java      # 业务异常
│   ├── Config.java                 # 应用配置
│   └── RequestValidator.java       # 请求校验
│
├── db/                             # 数据库层
│   ├── DatabaseVerticle.java       # 连接池 + 查询执行引擎
│   ├── FlywayMigration.java        # Flyway 迁移执行
│   ├── TransactionTemplate.java    # 声明式事务引擎
│   ├── TransactionContext.java     # 事务上下文
│   ├── TxContextHolder.java        # Vert.x Context 事务绑定
│   ├── Transactional.java          # @Transactional 注解
│   ├── AuditLogger.java            # 审计日志写入引擎
│   ├── AuditContext.java           # 审计上下文
│   ├── AuditContextHolder.java     # 审计上下文 ThreadLocal
│   ├── AuditAction.java            # 审计动作枚举
│   └── BatchOperations.java        # 批量操作工具
│
├── entity/                         # 实体类
│   ├── User.java
│   ├── Product.java
│   ├── Order.java / OrderItem.java
│   ├── Payment.java
│   ├── InventoryTransaction.java
│   ├── SysConfig.java
│   ├── SysDictType.java / SysDictData.java
│   ├── SysMenu.java / SysRole.java
│   ├── ScheduledTask.java
│   └── AuditLog.java
│
├── handlers/
│   ├── ErrorHandler.java           # 全局错误处理
│   └── UserHandler.java            # 用户处理器
│
├── repository/                     # 数据访问层
│   ├── UserRepository.java
│   ├── ProductRepository.java
│   ├── OrderRepository.java
│   ├── PaymentRepository.java
│   ├── InventoryTransactionRepository.java
│   ├── SysConfigRepository.java
│   ├── SysDictTypeRepository.java
│   ├── SysDictDataRepository.java
│   ├── SysMenuRepository.java
│   ├── ScheduledTaskRepository.java
│   └── AuditRepository.java
│
├── service/                        # 服务接口
│   ├── UserService.java ...等
│   └── impl/                       # 服务实现
│       ├── BaseServiceImpl.java    # 服务基类（单 repo）
│       ├── UserServiceImpl.java
│       ├── ProductServiceImpl.java
│       ├── OrderServiceImpl.java   # 多 repo + 事务
│       ├── PaymentServiceImpl.java # 多 repo + 事务
│       └── ...等
│
├── tasks/                          # 定时任务
│   ├── AuditArchiveTask.java       # 审计归档
│   └── ExampleTasks.java           # 示例任务
│
└── verticles/
    └── SchedulerVerticle.java      # 调度器 Verticle
```

## 分层架构

```
┌─────────────────────────────────────────────────┐
│                   HTTP 请求                      │
└───────────────────────┬─────────────────────────┘
                        ▼
┌─────────────────────────────────────────────────┐
│  MainVerticle (Router + 全局中间件)              │
│  CORS → Logger → BodyHandler → RequestID → Auth │
└───────────────────────┬─────────────────────────┘
                        ▼
┌─────────────────────────────────────────────────┐
│  API 层 (BaseApi 子类)                           │
│  参数解析 → 调用 Service → 响应封装              │
└───────────────────────┬─────────────────────────┘
                        ▼
┌─────────────────────────────────────────────────┐
│  Service 层                                      │
│  业务逻辑 + 事务编排 + 审计日志                  │
└───────────────────────┬─────────────────────────┘
                        ▼
┌─────────────────────────────────────────────────┐
│  Repository 层                                   │
│  SQL 执行 → 自动感知事务 (TxContextHolder)       │
└───────────────────────┬─────────────────────────┘
                        ▼
┌─────────────────────────────────────────────────┐
│  DatabaseVerticle (连接池 + 事务管理)            │
│  PostgreSQL                                      │
└─────────────────────────────────────────────────┘
```

## 关键设计决策

### 1. 编程式事务 vs AOP

项目使用 **TransactionTemplate.wrap()** 而非 Spring 风格的 AOP 自动代理。

原因：Vert.x 的异步模型下，AOP 拦截器难以正确管理 Future 链的事务边界。编程式 `wrap()` 让事务边界显式、可控。

### 2. TxContextHolder 自动路由

Repository 方法无需显式传 `TransactionContext`，通过 Vert.x Context-local 存储自动获取当前事务。

好处：Service 层代码简洁，同一 Repository 方法在事务内/外均可使用。

### 3. Demo 模式

数据库不可用时自动降级为 Demo 模式（`dbAvailable = false`），Service 层通过 `failIfUnavailable()` 短路返回空数据。

### 4. 双模式审计

- **logInTx**：嵌入当前事务，审计失败则整体回滚（适用于订单/支付）
- **log / logAsync**：独立连接，审计失败不影响主业务（适用于配置变更）

## 数据库迁移

| 版本 | 文件 | 说明 |
|------|------|------|
| V1 | `init_schema.sql` | 初始表结构 |
| V2 | `seed_data.sql` | 种子数据 |
| V3 | `scheduled_tasks.sql` | 定时任务表 |
| V4 | `config.sql` | 系统配置表 |
| V5 | `orders.sql` | 订单 + 订单项 |
| V6 | `payments.sql` | 支付表 |
| V7 | `inventory_transactions.sql` | 库存事务 |
| V8 | `audit_logs.sql` | 审计日志 |
| V9 | `audit_logs_archive.sql` | 审计归档 |
| V10 | `table_init.sql` | 表初始化 |
| V11 | `user_balance.sql` | 用户余额 |
| V12 | `rbac_tables.sql` | RBAC 角色权限 |
