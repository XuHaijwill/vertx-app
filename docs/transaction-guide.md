# 事务管理指南

## 核心原理

本项目采用 **编程式事务** 模式，而非 Spring 的 AOP 自动代理。`@Transactional` 注解仅作为配置标记，真正执行事务的是 `TransactionTemplate.wrap()`。

### 调用链

```
Service 方法 → txTemplate.wrap(block, timeoutMs)
  → DatabaseVerticle.withTransaction()
    → 开启事务，绑定 TxContextHolder
    → 执行 block（Repository 自动通过 TxContextHolder.current() 获取连接）
    → 成功：commit / 失败：rollback
    → 解绑 TxContextHolder
```

## 快速开始

### 1. 单仓库 Service（继承 BaseServiceImpl）

单仓库 Service 继承 `BaseServiceImpl` 即可自动获得基础设施：

```java
public class ProductServiceImpl extends BaseServiceImpl<ProductRepository>
    implements ProductService {

    public ProductServiceImpl(Vertx vertx) {
        super(vertx, ProductRepository::new);  // 自动创建 repo、audit
    }

    // dbAvailable 短路已内置
    // vertx、repo、audit 字段已内置
}
```

如需事务，自行创建 `TransactionTemplate`：

```java
public class ProductServiceImpl extends BaseServiceImpl<ProductRepository>
    implements ProductService {

    private final TransactionTemplate txTemplate;

    public ProductServiceImpl(Vertx vertx) {
        super(vertx, ProductRepository::new);
        this.txTemplate = new TransactionTemplate(vertx);
    }

    @Override
    public Future<Product> createProduct(JsonObject data) {
        // 参数校验放事务外（快速失败）
        String name = data.getString("name");
        if (name == null || name.isBlank()) {
            return Future.failedFuture(BusinessException.badRequest("name is required"));
        }

        return txTemplate.wrap(tx -> doCreateProduct(data), 30_000);
    }

    private Future<Product> doCreateProduct(JsonObject data) {
        return repo.insert(data)
            .compose(id -> repo.findById(id));
    }
}
```

### 2. 多仓库 Service（不继承 BaseServiceImpl）

多仓库 Service 需自行维护 `vertx`、`audit`、`dbAvailable` 字段：

```java
public class OrderServiceImpl implements OrderService {

    private final Vertx vertx;
    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final PaymentRepository paymentRepo;
    private final UserRepository userRepo;
    private final AuditLogger audit;
    private final TransactionTemplate txTemplate;
    private final boolean dbAvailable;

    public OrderServiceImpl(Vertx vertx) {
        this.vertx = vertx;
        this.orderRepo = new OrderRepository(vertx);
        this.orderItemRepo = new OrderItemRepository(vertx);
        this.paymentRepo = new PaymentRepository(vertx);
        this.userRepo = new UserRepository(vertx);
        this.audit = new AuditLogger(vertx);
        this.txTemplate = new TransactionTemplate(vertx);
        this.dbAvailable = DatabaseVerticle.getPool(vertx) != null;
    }

    @Override
    public Future<Order> createOrder(Long userId, BigDecimal total, String remark, JsonArray items) {
        if (!dbAvailable) {
            return Future.failedFuture("Database unavailable");
        }
        return txTemplate.wrap(tx -> doCreateOrder(userId, total, remark, items), 60_000);
    }
}
```

## TransactionTemplate API

### wrap(block)

默认超时 30 秒：

```java
return txTemplate.wrap(tx -> {
    return repo.findByIdForUpdate(id)
        .compose(entity -> repo.update(id, data));
});
```

### wrap(block, timeoutMs)

自定义超时（毫秒）：

```java
return txTemplate.wrap(tx -> doWork(), 60_000);  // 60秒超时
```

### wrap(block, annotation)

从 `@Transactional` 注解读取超时配置：

```java
@Transactional(timeoutMs = 60_000)
public Future<Order> createOrder(JsonObject order) {
    // 注意：注解本身不会自动生效，需配合 txTemplate.wrap()
    return txTemplate.wrap(tx -> doCreateOrder(order), annotation);
}
```

## Repository 自动感知事务

Repository 方法内部通过 `TxContextHolder.current()` 自动判断是否在事务中：

```java
// Repository 内部实现模式
public Future<Product> findByIdForUpdate(Long id) {
    TransactionContext tx = TxContextHolder.current();
    if (tx != null) {
        // 在事务中 → 用事务连接 + 行锁
        tx.tick();
        String sql = "SELECT * FROM products WHERE id = $1 FOR UPDATE";
        return queryOneInTx(tx.conn(), sql, Tuple.of(id));
    } else {
        // 非事务 → 用连接池
        return queryOne(sql, Tuple.of(id));
    }
}
```

**关键点：** `findByIdForUpdate`、`insertOrder` 等带 `ForUpdate`/`InTx` 后缀的方法，**必须在事务内调用**，否则走非事务分支（无行锁，可能产生并发问题）。

## 超时机制

- 默认超时：30 秒（`TransactionTemplate.DEFAULT_TX_TIMEOUT_MS`）
- 超时行为：底层 DB 连接被 force-close，事务自动回滚
- `TransactionContext.checkTimeout()`：在 90% 超时阈值时发出警告
- 建议：订单/支付等复杂操作使用 60 秒超时

## 注意事项

| 要点 | 说明 |
|------|------|
| **不支持嵌套事务** | `TxContextHolder.bind()` 检测到已存在事务会抛 `IllegalStateException` |
| **超时 = 连接强关** | 超过 timeoutMs 后连接被 force-close，事务回滚 |
| **校验放事务外** | 参数校验不要放进 `wrap()` 里面，避免无谓开事务 |
| **事务内不要用连接池方法** | 必须用 `tx.conn()` 执行 SQL，不要调 `DatabaseVerticle.query()` |
| **单 repo 继承 BaseServiceImpl** | 自带 `vertx`/`repo`/`audit`/`dbAvailable` |
| **多 repo 自己管理字段** | 不继承 BaseServiceImpl，自行创建 TransactionTemplate |

## 实际项目示例

### OrderServiceImpl（60秒事务）

```java
@Override
public Future<Order> createOrder(Long userId, BigDecimal total, String remark, JsonArray items) {
    if (!dbAvailable) {
        return Future.failedFuture("Database unavailable");
    }
    // 参数校验...
    return txTemplate.wrap(tx -> doCreateOrder(userId, total, remark, items), 60_000);
}

private Future<Order> doCreateOrder(Long userId, BigDecimal total, String remark, JsonArray items) {
    return userRepo.findByIdForUpdate(userId)
        .compose(user -> orderRepo.insertOrder(userId, total, remark))
        .compose(orderId -> orderItemRepo.insertItems(orderId, items).map(orderId))
        .compose(orderId -> productRepo.deductStock(orderId, items).map(orderId))
        .compose(orderId -> orderRepo.findById(orderId));
}
```

### PaymentServiceImpl（30秒事务）

```java
@Override
public Future<Payment> processPayment(Long orderId, String method) {
    if (!dbAvailable) return failIfUnavailableNull();
    return txTemplate.wrap(tx -> doProcessPayment(orderId, method), 30_000);
}
```
