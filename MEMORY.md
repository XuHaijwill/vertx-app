# vertx-app 事务管理 — 添加 withTransaction()

## 目标
为 DatabaseVerticle 添加 `withTransaction()` 工具方法，支持在事务中执行一组数据库操作，失败自动回滚。

## 关键改动

### DatabaseVerticle.java — 新增 `withTransaction()` 方法

**核心逻辑（3层 Future 组合）：**

```
pool.getConnection()
  └─ compose(conn)
       └─ conn.begin()  ← 返回 Future<Transaction>
            └─ compose(tx)
                 └─ block.apply(tx)  ← 用户 lambda
                      ├─ 成功 → tx.commit() → conn.close() → 返回 result
                      └─ 失败 → tx.rollback() → conn.close() → 返回 err
```

**关键设计点：**
- `pool.getConnection()` 从连接池借一个连接
- `conn.begin()` 在 Vert.x 5 返回 `Future<Transaction>`，需再 `.compose()`
- 用户 lambda 不需要手动调用 `commit()` / `rollback()`，由外层统一管理
- 无论成功/失败，`conn.close()` 都会执行，保证连接归还池中
- 外层 `.onFailure()` 只记录错误，实际失败原因由内层 `Future.failedFuture(err)` 传递

**编译踩坑（Vert.x 5 API 变化）：**
- `Pool` 没有 `begin()` 方法 → 需先 `pool.getConnection()` 再在连接上 `begin()`
- `conn.begin()` 返回 `Future<Transaction>` 而非同步 `Transaction` → 需额外一层 `compose()`

## 使用示例

```java
// 创建订单（涉及 orders + order_items 两张表）
return DatabaseVerticle.withTransaction(vertx, tx ->
    tx.preparedQuery(
        "INSERT INTO orders(user_id, total, status) VALUES ($1,$2,'PENDING') RETURNING id")
      .execute(Tuple.of(userId, total))
      .compose(row -> {
          long orderId = row.iterator().next().getLong("id");
          return tx.preparedQuery(
              "INSERT INTO order_items(order_id, product_id, qty) VALUES ($1,$2,$3)")
            .executeBatch(items);  // 批量插入
      })
      .map("Order created successfully")
);
```

## 状态
- [x] 编译通过（mvn compile — BUILD SUCCESS）
- [ ] 可选：添加 `withTransaction` 到 SchedulerVerticle 的 SQL 任务执行（如果 SQL 任务需要事务保证）
