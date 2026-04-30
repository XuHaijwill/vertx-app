# Vertx-App 事务管理架构分析

**分析时间**: 2026-04-30  
**项目**: vertx-app (Vert.x 5 + PostgreSQL)

---

## 一、架构概览

```
┌─────────────────────────────────────────────────────────────────────┐
│                          Service Layer                               │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────┐              │
│  │ OrderService│  │PaymentService│  │ProductService  │              │
│  └──────┬──────┘  └──────┬───────┘  └────────────────┘              │
│         │                │                                          │
│         └────────────────┼──────────────────────┐                   │
│                          │                      │                   │
│                          ▼                      ▼                   │
│         ┌────────────────────────────────────────────────┐          │
│         │    DatabaseVerticle.withTransaction()        │          │
│         │    Function<TransactionContext, Future<T>>     │          │
│         └────────────────────┬───────────────────────────┘          │
│                              │                                      │
└──────────────────────────────┼──────────────────────────────────────┘
                               │
┌──────────────────────────────┼──────────────────────────────────────┐
│                              ▼                                      │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                 TransactionContext                           │   │
│  │  ┌─────────────────┐  ┌─────────────────────────────────┐   │   │
│  │  │ SqlConnection   │  │ Metadata: opCount, elapsedMs,   │   │   │
│  │  │ (tx-scoped)     │  │ timeout, rollbackOnly           │   │   │
│  │  └─────────────────┘  └─────────────────────────────────┘   │   │
│  └────────────────────────────┬────────────────────────────────┘   │
│                               │                                     │
└───────────────────────────────┼─────────────────────────────────────┘
                                │
┌───────────────────────────────┼─────────────────────────────────────┐
│                       Repository Layer                               │
│                                │                                     │
│        ┌───────────────────────┼───────────────────────┐            │
│        │                       │                       │            │
│        ▼                       ▼                       ▼            │
│  ┌───────────┐          ┌───────────┐          ┌───────────┐      │
│  │OrderRepo  │          │PaymentRepo│          │ProductRepo│      │
│  │-----------│          │-----------│          │-----------│      │
│  │findById() │          │findById() │          │findById() │      │
│  │Pool-based │          │Pool-based │          │Pool-based │      │
│  │           │          │           │          │           │      │
│  │findById   │          │insert     │          │deductStock│      │
│  │ForUpdate()│          │Payment()  │          │(tx)       │      │
│  │(tx)       │          │(tx)       │          │           │      │
│  └───────────┘          └───────────┘          └───────────┘      │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
                                │
┌───────────────────────────────┼─────────────────────────────────────┐
│                       Database Layer                                 │
│                                │                                     │
│        ┌───────────────────────┼───────────────────────┐            │
│        │                       │                       │            │
│        ▼                       ▼                       ▼            │
│  ┌──────────┐            ┌──────────┐            ┌──────────┐      │
│  │Pool-based│            │ Context- │            │ Tx       │      │
│  │query()   │            │ based    │            │ helpers  │      │
│  │──────────│            │ ─────────│            │──────────│      │
│  │pool.     │            │ tx.conn()│            │queryInTx │      │
│  │prepared  │            │.prepared │            │updateInTx│      │
│  │Query()   │            │Query()   │            │          │      │
│  └──────────┘            └──────────┘            └──────────┘      │
│                                                                     │
│                   ┌────────────────────────┐                        │
│                   │   PgConnectionPool      │                        │
│                   │   (Vert.x PgClient)    │                        │
│                   └────────────────────────┘                        │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 二、核心组件分析

### 2.1 DatabaseVerticle — 连接池与事务管理器

**职责**:
- 管理 PostgreSQL 连接池（PgClient）
- 提供事务执行入口 `withTransaction()`
- 提供静态查询辅助方法

**关键 API**:

```java
// 非事务查询（Pool-based）
public static Future<RowSet<Row>> query(Vertx vertx, String sql, Tuple params);

// 事务执行器（推荐API）
public static <T> Future<T> withTransaction(
    Vertx vertx,
    Function<TransactionContext, Future<T>> block,
    int timeoutMs
);
```

**事务流程**:

```
withTransaction() 执行流程
┌─────────────────────────────────────────────────────────────┐
│ 1. pool.getConnection()                                     │
│    └─ 从连接池获取连接                                        │
│                                                             │
│ 2. 创建 TransactionContext 包装连接                          │
│    └─ new TransactionContext(conn, timeoutMs)              │
│                                                             │
│ 3. 启动超时定时器                                            │
│    └─ vertx.setTimer(timeoutMs, id -> conn.close())        │
│                                                             │
│ 4. conn.begin()                                             │
│    └─ 开启数据库事务                                         │
│                                                             │
│ 5. 执行用户代码 block.apply(txCtx)                           │
│    └─ 用户编排多表操作（跨多个 Repository）                   │
│                                                             │
│ 6. 成功分支:                                                 │
│    ├─ 检查 rollbackOnly 标记                                 │
│    ├─ tx.commit() 或 tx.rollback()                          │
│    ├─ 取消超时定时器                                        │
│    └─ conn.close() 归还连接                                  │
│                                                             │
│ 7. 失败分支:                                                 │
│    ├─ tx.rollback()                                         │
│    ├─ 取消超时定时器                                        │
│    ├─ conn.close() 归还连接                                  │
│    └─ 返回 Future.failedFuture(err)                        │
└─────────────────────────────────────────────────────────────┘
```

**超时保护机制**:

```java
// 默认超时30秒
public static final int DEFAULT_TX_TIMEOUT_MS = 30_000;

// 超时后强制关闭连接（触发自动rollback）
long timerId = vertx.setTimer(timeoutMs, id -> {
    LOG.warn("[TX] Timeout after {}ms ({} ops) — force closing",
        timeoutMs, txCtx.operationCount());
    conn.close();  // 强制关闭，PostgreSQL会自动rollback
});
```

---

### 2.2 TransactionContext — 事务上下文包装器

**职责**:
- 包装事务范围的 SqlConnection
- 提供操作计数和超时检查
- 支持 rollback-only 标记

**核心字段**:

```java
public class TransactionContext {
    private final SqlConnection conn;      // 事务范围的连接
    private final long startedAt;          // 创建时间戳
    private final long timeoutMs;          // 超时预算
    private int operationCount;            // 已执行SQL数量
    private boolean rollbackOnly;          // 回滚标记
}
```

**核心方法**:

| 方法 | 说明 |
|------|------|
| `conn()` | 获取底层 SqlConnection |
| `tick()` | 操作计数+1，返回this（链式调用） |
| `elapsedMs()` | 已用时间（毫秒） |
| `setRollbackOnly()` | 标记为仅回滚（commit时跳过提交） |
| `isRollbackOnly()` | 检查是否标记回滚 |
| `checkTimeout()` | 检查超时（90%阈值抛异常） |

**设计亮点**:
1. **统一API** — 所有 Repository 事务方法签名一致 `(TransactionContext tx, ...)`
2. **可观测性** — 操作计数和耗时追踪，便于监控和调优
3. **扩展性** — 可添加嵌套事务检测、分布式追踪等

---

### 2.3 Repository 层 — 双轨方法设计

每个 Repository 有两类方法：

**① Pool-based（非事务）**:
```java
// 使用连接池直接执行，无事务
public Future<JsonObject> findById(Long id) {
    String sql = "SELECT * FROM orders WHERE id = $1";
    return DatabaseVerticle.query(vertx, sql, Tuple.of(id))
        .map(rows -> toJsonList(rows).get(0));
}
```

**② Context-based（事务内）**:
```java
// 接收 TransactionContext，在事务内执行
public Future<JsonObject> findByIdForUpdate(TransactionContext tx, Long id) {
    tx.tick();  // 记录操作计数
    String sql = "SELECT * FROM orders WHERE id = $1 FOR UPDATE";
    return DatabaseVerticle.queryOneInTx(tx.conn(), sql, Tuple.of(id));
}
```

**方法命名约定**:

| 后缀 | 说明 | 示例 |
|------|------|------|
| `InTx` | 事务内方法 | `updateStatusInTx`, `findItemsByOrderIdInTx` |
| `ForUpdate` | 带行锁查询 | `findByIdForUpdate` |
| 无后缀 | Pool-based | `findById`, `findAll` |

---

### 2.4 Service 层 — 事务编排

**典型模式**:

```java
public Future<JsonObject> createOrder(JsonObject order) {
    return DatabaseVerticle.withTransaction(vertx, tx ->
        // Step 1: Lock user (防止并发刷单)
        userRepo.findByIdForUpdate(tx, userId)
            // Step 2: Insert order
            .compose(user -> orderRepo.insertOrder(tx, userId, total, remark))
            // Step 3: Insert items (sequential)
            .compose(orderId -> insertItemsSequence(tx, orderId, items, 0))
            // Step 4: Deduct stock (sequential)
            .compose(orderId -> deductStockSequence(tx, orderId, items, 0))
            // Step 5: Return result
            .map(orderId -> buildResult(orderId, userId, total)),
    60_000  // 自定义超时60秒
    );
}
```

**关键原则**:
- 所有跨表操作必须用 `withTransaction()` 包装
- 每个 Repository 方法调用传入相同的 `tx` 参数
- 使用 `compose()` 串联异步操作（Vert.x Future 链式调用）
- 关键行用 `FOR UPDATE` 锁定（防止 TOCTOU）

---

## 三、事务场景分析

### 3.1 OrderService.createOrder — 3表事务

```
┌─────────────────────────────────────────────────────────────┐
│                 Transaction: createOrder                     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  [BEGIN]                                                    │
│     │                                                       │
│     ├─► Lock user row (users)                               │
│     │      └─ SELECT ... FROM users WHERE id=? FOR UPDATE   │
│     │                                                       │
│     ├─► INSERT order (orders)                              │
│     │      └─ RETURNING id                                  │
│     │                                                       │
│     ├─► Loop: INSERT order_items                            │
│     │      └─ N rows (sequential)                           │
│     │                                                       │
│     ├─► Loop: Deduct stock (products)                      │
│     │      └─ UPDATE products SET stock=stock-?            │
│     │      └─ INSERT inventory_transactions (ledger)        │
│     │                                                       │
│  [COMMIT] — all success                                     │
│  [ROLLBACK] — any failure                                   │
│                                                             │
└─────────────────────────────────────────────────────────────┘

涉及表: users, orders, order_items, products, inventory_transactions (5表)
```

### 3.2 OrderService.cancelOrder — TOCTOU 修复示例

**修复前（有竞态条件）**:
```java
// ❌ 错误示例：读在事务外
public Future<JsonObject> cancelOrder_BAD(Long orderId) {
    return orderRepo.findById(orderId)  // 事务外读取
        .compose(order -> {
            if ("cancelled".equals(order.getString("status"))) {
                return Future.failedFuture("Already cancelled");
            }
            // 并发请求可能都通过了这个检查！
            return DatabaseVerticle.withTransaction(vertx, tx ->
                orderRepo.updateStatusInTx(tx, orderId, "cancelled")
            );
        });
}
```

**修复后（FOR UPDATE 锁）**:
```java
// ✓ 正确：读和写在同一事务内，且加行锁
public Future<JsonObject> cancelOrder(Long orderId) {
    return DatabaseVerticle.withTransaction(vertx, tx ->
        orderRepo.findByIdForUpdate(tx, orderId)  // FOR UPDATE 锁定
            .compose(order -> {
                // 第二个并发请求会阻塞在这里
                // 等第一个事务提交后，它看到的是已更新的状态
                if ("cancelled".equals(order.getString("status"))) {
                    return Future.failedFuture("Already cancelled");
                }
                return orderRepo.updateStatusInTx(tx, orderId, "cancelled");
            }),
        30_000
    );
}
```

### 3.3 PaymentService.processPayment — 5表跨4个Repository

```
┌─────────────────────────────────────────────────────────────┐
│              Transaction: processPayment                     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  [BEGIN]                                                    │
│     │                                                       │
│     ├─► Lock order (OrderRepository)                       │
│     │      └─ findByIdForUpdate(tx, orderId)                │
│     │                                                       │
│     ├─► Lock user (UserRepository)                          │
│     │      └─ findByIdForUpdate(tx, userId)                │
│     │                                                       │
│     ├─► Deduct balance (UserRepository)                    │
│     │      └─ UPDATE users SET balance=balance-?           │
│     │                                                       │
│     ├─► INSERT payment (PaymentRepository)                  │
│     │      └─ INSERT payments ... RETURNING id             │
│     │                                                       │
│     ├─► Update payment status                               │
│     │      └─ UPDATE payments SET status='completed'       │
│     │                                                       │
│     ├─► Update order status                                │
│     │      └─ UPDATE orders SET status='completed'          │
│     │                                                       │
│     ├─► Confirm stock deduction (ProductRepository)         │
│     │      └─ (已在createOrder时扣除，此处可选)              │
│     │                                                       │
│     ├─► Increment user.order_count (UserRepository)         │
│     │      └─ UPDATE users SET order_count=order_count+1   │
│     │                                                       │
│  [COMMIT]                                                   │
│                                                             │
└─────────────────────────────────────────────────────────────┘

涉及表: payments, orders, users, products, inventory_transactions
涉及Repository: PaymentRepository, OrderRepository, UserRepository, ProductRepository
```

---

## 四、架构优势

### 4.1 统一的事务API

| 对比项 | 当前架构 | 传统 JDBC |
|--------|----------|----------|
| 事务参数传递 | `TransactionContext` 统一包装 | `Connection` 直接传递 |
| 方法签名 | 一致：`(TransactionContext tx, ...)` | 混乱：可能用 ThreadLocal 或显式传参 |
| 可观测性 | 内置 `tick()` 计数、`elapsedMs()` | 需手动埋点 |
| 超时保护 | 自动定时器强制关闭 | 需外部框架支持 |

### 4.2 明确的 Repository 方法分类

```
Repository 方法分类
┌────────────────────────────────────────────────────────┐
│ 非事务方法（Pool-based）                                │
│   ├─ findById(Long id)                                 │
│   ├─ findAll()                                         │
│   ├─ findByXxx(...)                                    │
│   └─ search(...)                                       │
│                                                        │
│ 事务方法（Context-based）                               │
│   ├─ findByIdForUpdate(tx, id)  — 行锁查询             │
│   ├─ insertXxx(tx, ...)         — 插入                 │
│   ├─ updateXxxInTx(tx, ...)     — 更新                 │
│   └─ deleteXxxInTx(tx, ...)     — 删除                 │
└────────────────────────────────────────────────────────┘
```

### 4.3 可配置的超时机制

```java
// 不同场景不同超时
withTransaction(vertx, block, 30_000);  // 默认30秒
withTransaction(vertx, block, 60_000);  // 复杂订单60秒
withTransaction(vertx, block, 5_000);  // 简单更新5秒
```

### 4.4 TOCTOU 防护

- 关键状态检查必须在事务内
- 使用 `FOR UPDATE` 锁定关键行
- 读后写操作必须在同一事务中完成

---

## 五、潜在问题与改进建议

### 5.1 超时后连接状态问题

**当前实现**:
```java
vertx.setTimer(timeoutMs, id -> {
    conn.close();  // 强制关闭
});
```

**问题**: 
- 超时后连接被强制关闭，但用户的 Future 可能仍在等待
- 返回的错误信息不够明确

**建议**:
```java
vertx.setTimer(timeoutMs, id -> {
    txCtx.setRollbackOnly();  // 先标记回滚
    conn.close();
    // 在 block 返回的 Future 中检查超时状态
});
```

### 5.2 缺少声明式事务

**当前**: 手动编排 `withTransaction()`
**建议**: 可引入注解式事务（类似 Spring `@Transactional`）

```java
// 理想形式（需要 AOP 支持）
@Transactional(timeoutMs = 30000)
public Future<JsonObject> createOrder(JsonObject order) {
    // 无需手动包装 withTransaction
    return userRepo.findByIdForUpdate(...)...;
}
```

### 5.3 批量操作性能

**当前**: 顺序执行每个 item
```java
for (int i = 0; i < items.size(); i++) {
    insertItem(tx, orderId, items.getJsonObject(i));
}
```

**建议**: 使用批量插入
```java
// 批量插入更高效
String sql = "INSERT INTO order_items (order_id, product_id, quantity, price) " +
             "VALUES ($1, $2, $3, $4)";
List<Tuple> batch = items.stream()
    .map(item -> Tuple.of(orderId, item.getLong("productId"), 
                          item.getInteger("quantity"), item.getBigDecimal("price")))
    .toList();
return tx.conn().preparedQuery(sql).executeBatch(batch);
```

### 5.4 缺少分布式事务支持

**当前**: 仅支持单数据库事务
**未来**: 如需跨服务事务，需引入：
- Saga 模式
- TCC（Try-Confirm-Cancel）
- Seata 等分布式事务框架

### 5.5 异常类型不统一

**当前**: RuntimeException / BusinessException 混用
**建议**: 统一使用 BusinessException 并携带事务上下文

```java
public class TransactionException extends BusinessException {
    private final TransactionContext txContext;
    
    public TransactionException(String message, TransactionContext tx) {
        super(message);
        this.txContext = tx;
    }
    
    public TransactionContext getTransactionContext() {
        return txContext;
    }
}
```

---

## 六、最佳实践总结

### 6.1 何时使用事务

| 场景 | 是否需要事务 |
|------|-------------|
| 单表单行查询 | ❌ 不需要 |
| 单表单行更新（无状态依赖） | ❌ 不需要 |
| 单行更新（依赖行内状态） | ⚠️ 可选（考虑 FOR UPDATE） |
| 跨表操作 | ✅ 必须 |
| 读后写（检查状态再更新） | ✅ 必须 + FOR UPDATE |
| 批量操作（需原子性） | ✅ 必须 |

### 6.2 事务代码模板

```java
// 标准模板
public Future<T> businessOperation(params) {
    return DatabaseVerticle.withTransaction(vertx, tx ->
        // 1. 锁定关键行
        repo.findByIdForUpdate(tx, id)
            .compose(entity -> {
                // 2. 业务验证
                if (!isValid(entity)) {
                    return Future.failedFuture(BusinessException.badRequest("..."));
                }
                return Future.succeededFuture(entity);
            })
            .compose(entity -> {
                // 3. 执行操作（跨多个 Repository）
                return repo1.updateInTx(tx, ...)
                    .compose(v -> repo2.insertInTx(tx, ...))
                    .compose(v -> repo3.updateInTx(tx, ...));
            })
            .map(result -> result),
        30_000  // 合理的超时
    );
}
```

### 6.3 Repository 方法命名规范

```java
// ✓ 推荐：清晰区分事务/非事务
public Future<JsonObject> findById(Long id);                          // 非事务
public Future<JsonObject> findByIdForUpdate(TransactionContext tx, Long id);  // 事务+锁
public Future<Void> updateStatusInTx(TransactionContext tx, ...);     // 事务

// ✗ 不推荐：命名混乱
public Future<JsonObject> get(Long id);                               // 含义不明
public Future<JsonObject> getByIdTx(Connection conn, Long id);        // 旧API，不一致
```

---

## 七、总结

### 当前架构评价

| 维度 | 评分 | 说明 |
|------|------|------|
| API 一致性 | ⭐⭐⭐⭐⭐ | TransactionContext 统一参数传递 |
| 并发安全 | ⭐⭐⭐⭐ | FOR UPDATE 解决 TOCTOU |
| 超时保护 | ⭐⭐⭐⭐ | 定时器强制关闭，但状态处理可改进 |
| 可观测性 | ⭐⭐⭐⭐ | 操作计数、耗时追踪 |
| 扩展性 | ⭐⭐⭐⭐ | TransactionContext 可扩展 |
| 批量性能 | ⭐⭐⭐ | 顺序执行，可优化为批量 |

### 关键收获

1. **统一的事务上下文** — TransactionContext 作为唯一事务参数，避免 ThreadLocal 和 Connection 混用
2. **明确的 Repository 方法分类** — Pool-based vs Context-based 一目了然
3. **TOCTOU 防护意识** — 读后写必须在同一事务内 + FOR UPDATE
4. **超时保护** — 定时器强制关闭防止连接泄漏
5. **可观测性设计** — 操作计数和耗时追踪，便于监控

### 下一步优化方向

1. 引入批量操作 API（提升性能）
2. 统一异常类型（携带事务上下文）
3. 考虑声明式事务注解（减少样板代码）
4. 添加事务追踪和日志审计

---

**文档版本**: 1.0  
**作者**: OpenClaw Agent  
**最后更新**: 2026-04-30
