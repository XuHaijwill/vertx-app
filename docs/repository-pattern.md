# Repository 模式

## 概述

Repository 层封装数据库操作，提供两种查询模式：**连接池模式**和**事务感知模式**。

## 查询模式

### 1. 连接池模式（Pool-based）

通过 `DatabaseVerticle.query()` 执行，从连接池获取连接。适用于非事务场景。

```java
public Future<List<User>> findAll() {
    String sql = "SELECT * FROM users ORDER BY id";
    return DatabaseVerticle.query(vertx, sql, Tuple.tuple())
        .map(rows -> User.toList(rows));
}
```

### 2. 事务感知模式（Context-based）

自动检测当前事务，优先使用事务连接。这是**推荐模式**。

```java
public Future<User> findByIdForUpdate(Long id) {
    TransactionContext tx = TxContextHolder.current();
    if (tx != null) {
        // 事务内 → 用事务连接 + 行锁
        tx.tick();
        String sql = "SELECT * FROM users WHERE id = $1 FOR UPDATE";
        return DatabaseVerticle.queryOneInTx(tx.conn(), sql, Tuple.of(id))
            .map(row -> row != null ? User.fromRow(row) : null);
    } else {
        // 非事务 → 用连接池
        return DatabaseVerticle.queryOne(vertx,
            "SELECT * FROM users WHERE id = $1", Tuple.of(id))
            .map(row -> row != null ? User.fromRow(row) : null);
    }
}
```

### 3. Auto-route 模式

部分 Repository 提供 `queryAuto()` 方法，自动路由到事务或连接池：

```java
private Future<RowSet<Row>> queryAuto(String sql, Tuple params) {
    TransactionContext tx = TxContextHolder.current();
    if (tx != null) {
        tx.tick();
        return DatabaseVerticle.queryInTx(tx.conn(), sql, params);
    } else {
        return DatabaseVerticle.query(vertx, sql, params);
    }
}
```

## DatabaseVerticle 查询 API

### 静态方法

| 方法 | 说明 |
|------|------|
| `query(vertx, sql, params)` | 连接池查询，返回 `RowSet<Row>` |
| `queryOne(vertx, sql, params)` | 连接池查询单行 |
| `queryInTx(conn, sql, params)` | 事务连接查询 |
| `queryOneInTx(conn, sql, params)` | 事务连接查询单行 |
| `withTransaction(vertx, block, timeout)` | 开启事务 |
| `getPool(vertx)` | 获取连接池（null = Demo 模式） |

### 连接池获取

```java
Pool pool = DatabaseVerticle.getPool(vertx);
if (pool == null) {
    // Demo 模式，返回空数据
}
```

## 实体映射

### fromRow 模式

从数据库 Row 映射到实体：

```java
public class User {
    private Long id;
    private String username;
    private String email;
    // ...

    public static User fromRow(Row row) {
        User u = new User();
        u.id = row.getLong("id");
        u.username = row.getString("username");
        u.email = row.getString("email");
        return u;
    }

    public static List<User> toList(RowSet<Row> rows) {
        return StreamSupport.stream(rows.spliterator(), false)
            .map(User::fromRow)
            .collect(Collectors.toList());
    }

    public static User toOne(RowSet<Row> rows) {
        var it = rows.iterator();
        return it.hasNext() ? fromRow(it.next()) : null;
    }
}
```

### fromJson 模式

从 JSON 请求体映射到实体：

```java
public static User fromJson(JsonObject json) {
    User u = new User();
    u.username = json.getString("username");
    u.email = json.getString("email");
    // 注意：id 通常不由客户端提供
    return u;
}
```

### toJson 模式

实体转 JSON 用于响应：

```java
public JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.put("id", id);
    json.put("username", username);
    json.put("email", email);
    return json;
}
```

## 分页查询

### 标准 SQL 分页

```java
public Future<PageResult<User>> findPaginated(int page, int size) {
    int offset = (page - 1) * size;
    String countSql = "SELECT COUNT(*) AS total FROM users";
    String dataSql = "SELECT * FROM users ORDER BY id LIMIT $1 OFFSET $2";

    return DatabaseVerticle.query(vertx, countSql, Tuple.tuple())
        .compose(countRows -> {
            long total = countRows.iterator().next().getLong("total");
            return DatabaseVerticle.query(vertx, dataSql, Tuple.of(size, offset))
                .map(rows -> new PageResult<>(User.toList(rows), total, page, size));
        });
}
```

### 动态条件搜索

```java
public Future<PageResult<User>> search(String username, String email,
                                        String status, int page, int size) {
    StringBuilder where = new StringBuilder("WHERE 1=1");
    List<Object> params = new ArrayList<>();
    int idx = 1;

    if (username != null && !username.isBlank()) {
        where.append(" AND username LIKE ").append("$").append(idx++);
        params.add("%" + username + "%");
    }
    if (email != null && !email.isBlank()) {
        where.append(" AND email LIKE ").append("$").append(idx++);
        params.add("%" + email + "%");
    }
    if (status != null) {
        where.append(" AND status = ").append("$").append(idx++);
        params.add(status);
    }

    String countSql = "SELECT COUNT(*) AS total FROM users " + where;
    String dataSql = "SELECT * FROM users " + where +
                     " ORDER BY id LIMIT $" + idx++ + " OFFSET $" + idx;
    params.add(size);
    params.add((page - 1) * size);

    Tuple tuple = Tuple.tuple(params.toArray());
    // ... 执行查询
}
```

## 最佳实践

1. **事务操作用 ForUpdate**：需要行锁时用 `SELECT ... FOR UPDATE`，防止 TOCTOU 并发问题
2. **参数化查询**：始终用 `$1, $2...` 占位符，防止 SQL 注入
3. **分页必须有 count**：`PageResult` 需要 total 才能计算 pages
4. **动态 SQL 注意参数索引**：先构建 WHERE，再追加 LIMIT/OFFSET
5. **toList/toOne 静态方法**：所有实体类都应提供
6. **null 检查**：`toOne` 可能返回 null，API 层需处理 404
