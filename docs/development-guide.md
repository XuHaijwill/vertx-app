# 开发指南

## 新增 API 模块

### 1. 创建 Entity

`src/main/java/com/example/entity/Foo.java`：

```java
package com.example.entity;

import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class Foo {
    private Long id;
    private String name;
    // ... 其他字段

    // --- Row 映射 ---
    public static Foo fromRow(Row row) {
        Foo f = new Foo();
        f.id = row.getLong("id");
        f.name = row.getString("name");
        return f;
    }

    public static List<Foo> toList(RowSet<Row> rows) {
        return StreamSupport.stream(rows.spliterator(), false)
            .map(Foo::fromRow).collect(Collectors.toList());
    }

    public static Foo toOne(RowSet<Row> rows) {
        var it = rows.iterator();
        return it.hasNext() ? fromRow(it.next()) : null;
    }

    // --- JSON 映射 ---
    public static Foo fromJson(JsonObject json) {
        Foo f = new Foo();
        f.name = json.getString("name");
        return f;
    }

    public JsonObject toJson() {
        return new JsonObject()
            .put("id", id)
            .put("name", name);
    }

    // getter/setter ...
}
```

### 2. 创建 Repository

`src/main/java/com/example/repository/FooRepository.java`：

```java
package com.example.repository;

import com.example.db.DatabaseVerticle;
import com.example.db.TransactionContext;
import com.example.db.TxContextHolder;
import com.example.entity.Foo;
import com.example.core.PageResult;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.List;

public class FooRepository {

    protected final Vertx vertx;

    public FooRepository(Vertx vertx) {
        this.vertx = vertx;
    }

    public Future<List<Foo>> findAll() {
        return DatabaseVerticle.query(vertx,
            "SELECT * FROM foo ORDER BY id", Tuple.tuple())
            .map(Foo::toList);
    }

    public Future<Foo> findById(Long id) {
        return DatabaseVerticle.queryOne(vertx,
            "SELECT * FROM foo WHERE id = $1", Tuple.of(id))
            .map(row -> row != null ? Foo.fromRow(row) : null);
    }

    public Future<Foo> findByIdForUpdate(Long id) {
        TransactionContext tx = TxContextHolder.current();
        if (tx != null) {
            tx.tick();
            return DatabaseVerticle.queryOneInTx(tx.conn(),
                "SELECT * FROM foo WHERE id = $1 FOR UPDATE", Tuple.of(id))
                .map(row -> row != null ? Foo.fromRow(row) : null);
        }
        return findById(id);
    }

    public Future<Long> insert(Foo foo) {
        return DatabaseVerticle.queryOne(vertx,
            "INSERT INTO foo (name) VALUES ($1) RETURNING id",
            Tuple.of(foo.getName()))
            .map(row -> row.getLong("id"));
    }

    public Future<Foo> update(Long id, Foo foo) {
        return DatabaseVerticle.queryOne(vertx,
            "UPDATE foo SET name = $2 WHERE id = $1 RETURNING *",
            Tuple.of(id, foo.getName()))
            .map(row -> row != null ? Foo.fromRow(row) : null);
    }

    public Future<Boolean> delete(Long id) {
        return DatabaseVerticle.query(vertx,
            "DELETE FROM foo WHERE id = $1", Tuple.of(id))
            .map(rows -> rows.rowCount() > 0);
    }

    public Future<PageResult<Foo>> findPaginated(int page, int size) {
        int offset = (page - 1) * size;
        return DatabaseVerticle.query(vertx,
            "SELECT COUNT(*) AS total FROM foo", Tuple.tuple())
            .compose(countRows -> {
                long total = countRows.iterator().next().getLong("total");
                return DatabaseVerticle.query(vertx,
                    "SELECT * FROM foo ORDER BY id LIMIT $1 OFFSET $2",
                    Tuple.of(size, offset))
                    .map(rows -> new PageResult<>(Foo.toList(rows), total, page, size));
            });
    }
}
```

### 3. 创建 Service 接口

`src/main/java/com/example/service/FooService.java`：

```java
package com.example.service;

import com.example.core.PageResult;
import com.example.entity.Foo;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import java.util.List;

public interface FooService {
    Future<List<Foo>> findAll();
    Future<Foo> findById(Long id);
    Future<Foo> create(JsonObject data);
    Future<Foo> update(Long id, JsonObject data);
    Future<Boolean> delete(Long id);
    Future<PageResult<Foo>> findPaginated(int page, int size);
}
```

### 4. 创建 Service 实现

**单仓库（继承 BaseServiceImpl）：**

```java
package com.example.service.impl;

import com.example.db.AuditLogger;
import com.example.db.AuditContext;
import com.example.db.AuditContextHolder;
import com.example.db.AuditAction;
import com.example.db.DatabaseVerticle;
import com.example.db.TransactionTemplate;
import com.example.core.BusinessException;
import com.example.core.PageResult;
import com.example.entity.Foo;
import com.example.repository.FooRepository;
import com.example.service.FooService;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class FooServiceImpl extends BaseServiceImpl<FooRepository>
    implements FooService {

    private final TransactionTemplate txTemplate;

    public FooServiceImpl(Vertx vertx) {
        super(vertx, FooRepository::new);
        this.txTemplate = new TransactionTemplate(vertx);
    }

    @Override
    public Future<List<Foo>> findAll() {
        if (!dbAvailable) return failIfUnavailable();
        return repo.findAll();
    }

    @Override
    public Future<Foo> findById(Long id) {
        if (!dbAvailable) return failIfUnavailableNull();
        return repo.findById(id);
    }

    @Override
    public Future<Foo> create(JsonObject data) {
        if (!dbAvailable) {
            return Future.failedFuture("Database unavailable");
        }
        Foo foo = Foo.fromJson(data);
        return repo.insert(foo)
            .compose(id -> repo.findById(id))
            .compose(created -> {
                AuditContext ctx = AuditContextHolder.current();
                if (ctx != null && dbAvailable) {
                    return audit.log(ctx, AuditAction.AUDIT_CREATE,
                        "foo", created.getId(), null, created.toJson())
                        .map(created);
                }
                return Future.succeededFuture(created);
            });
    }

    // ... update, delete 类似
}
```

### 5. 创建 API

`src/main/java/com/example/api/FooApi.java`：

```java
package com.example.api;

import com.example.service.FooService;
import com.example.service.impl.FooServiceImpl;
import io.vertx.ext.web.Router;

public class FooApi extends BaseApi {

    private final FooService service;

    public FooApi(io.vertx.core.Vertx vertx) {
        super(vertx);
        this.service = new FooServiceImpl(vertx);
    }

    @Override
    public void registerRoutes(Router router, String contextPath) {
        String base = contextPath + "/api/foo";

        router.get(base).handler(ctx -> {
            int page = queryInt(ctx, "page", 1);
            int size = queryIntClamped(ctx, "size", 20, 1, 100);
            respondPaginated(ctx, service.findPaginated(page, size));
        });

        router.get(base + "/:id").handler(ctx -> {
            Long id = parseId(ctx.pathParam("id"));
            if (id == null) { badRequest(ctx, "Invalid id"); return; }
            respond(ctx, service.findById(id)
                .onFailure(err -> notFound(ctx, "Foo not found")));
        });

        router.post(base).handler(ctx -> {
            JsonObject body = bodyJson(ctx);
            if (body == null) { badRequest(ctx, "Request body required"); return; }
            respondCreated(ctx, service.create(body));
        });

        router.put(base + "/:id").handler(ctx -> {
            Long id = parseId(ctx.pathParam("id"));
            if (id == null) { badRequest(ctx, "Invalid id"); return; }
            JsonObject body = bodyJson(ctx);
            respond(ctx, service.update(id, body));
        });

        router.delete(base + "/:id").handler(ctx -> {
            Long id = parseId(ctx.pathParam("id"));
            if (id == null) { badRequest(ctx, "Invalid id"); return; }
            respondDeleted(ctx, service.delete(id));
        });
    }
}
```

### 6. 注册路由

在 `MainVerticle.registerApis()` 中添加：

```java
new FooApi(vertx).registerRoutes(router, contextPath);
```

### 7. 数据库迁移

创建 `src/main/resources/db/migration/V13__foo.sql`：

```sql
CREATE TABLE foo (
    id      BIGSERIAL PRIMARY KEY,
    name    VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP DEFAULT NOW()
);
```

## 编码规范

### 命名

| 类型 | 规范 | 示例 |
|------|------|------|
| Entity | PascalCase | `User`, `SysConfig` |
| Repository | Entity + Repository | `UserRepository` |
| Service 接口 | Entity + Service | `UserService` |
| Service 实现 | Entity + ServiceImpl | `UserServiceImpl` |
| API | Entity + Api | `UserApi` |
| 表名 | snake_case | `sys_config` |
| 列名 | snake_case | `config_key` |

### 异常处理

```java
// 业务异常
throw BusinessException.badRequest("name is required");
throw BusinessException.notFound("User not found");

// 在 API 层统一处理
respond(ctx, service.findById(id));  // 自动处理成功/失败
```

### 事务方法模式

```java
// ✅ 正确：参数校验在事务外
public Future<Order> createOrder(JsonObject data) {
    Long userId = data.getLong("userId");
    if (userId == null) {
        return Future.failedFuture(BusinessException.badRequest("userId required"));
    }
    return txTemplate.wrap(tx -> doCreateOrder(data), 60_000);
}

// ❌ 错误：在事务内做校验，浪费事务资源
public Future<Order> createOrder(JsonObject data) {
    return txTemplate.wrap(tx -> {
        Long userId = data.getLong("userId");
        if (userId == null) {
            return Future.failedFuture("userId required");  // 已开事务！
        }
        return doCreateOrder(data);
    }, 60_000);
}
```
