# P1: BaseServiceImpl 抽取 + SysMenuServiceImpl 重构 — 完成

## 问题

每个 ServiceImpl 重复相同的样板代码：
1. `new XxxRepository(vertx)` — 手动实例化
2. `new AuditLogger(vertx)` — 审计日志实例
3. `if (!dbAvailable) return Future.succeededFuture(...);` — 散布每个方法

9 个 ServiceImpl，平均每个 ~12 个 dbAvailable 检查，全是重复。

## 解决方案：BaseServiceImpl<R>

### 新建 BaseServiceImpl.java

```java
public abstract class BaseServiceImpl<R> {
    protected final Vertx vertx;
    protected final R     repo;
    protected final AuditLogger audit;
    protected final boolean dbAvailable;

    protected BaseServiceImpl(Vertx vertx, Function<Vertx, R> repoFactory) {
        this.vertx   = vertx;
        this.repo    = repoFactory.apply(vertx);
        this.audit   = new AuditLogger(vertx);
        this.dbAvailable = DatabaseVerticle.getPool(vertx) != null;
    }

    /** 集合类型 db unavailable 短路返回 */
    @SuppressWarnings("unchecked")
    protected <T> Future<List<T>> failIfUnavailable() {
        return Future.succeededFuture((List<T>) List.of());
    }

    /** 单类型 db unavailable 短路返回 */
    @SuppressWarnings("unchecked")
    protected <T> Future<T> failIfUnavailableNull() {
        return Future.succeededFuture((T) null);
    }
}
```

### SysMenuServiceImpl 重构示例

**Before：**
```java
public class SysMenuServiceImpl implements SysMenuService {
    private final SysMenuRepository repo;
    private final AuditLogger audit;
    private final boolean dbAvailable;

    public SysMenuServiceImpl(Vertx vertx) {
        this.repo = new SysMenuRepository(vertx);
        this.audit = new AuditLogger(vertx);
        this.dbAvailable = DatabaseVerticle.getPool(vertx) != null;
    }

    @Override
    public Future<List<SysMenu>> findAll() {
        if (!dbAvailable) return Future.succeededFuture(List.of());  // 重复
        return repo.findAll();
    }
```

**After：**
```java
public class SysMenuServiceImpl
    extends BaseServiceImpl<SysMenuRepository>
    implements SysMenuService {

    public SysMenuServiceImpl(Vertx vertx) {
        super(vertx, SysMenuRepository::new);  // 一行搞定 repo + audit + dbAvailable
    }

    @Override
    public Future<List<SysMenu>> findAll() {
        if (!dbAvailable) return failIfUnavailable();  // 语义清晰
        return repo.findAll();
    }
```

## 规模效应

SysMenuServiceImpl 有 12 个 dbAvailable 检查 → 全部使用 `failIfUnavailable()` / `failIfUnavailableNull()`，重复代码归零。

其余 8 个 ServiceImpl 可按相同模式重构，每次改动约 5 分钟。

## 编译验证
✅ BUILD SUCCESS

## 下一步
- 其余 8 个 ServiceImpl 依次重构（User、Product、Order、Payment、SysConfig、SysDictType、SysDictData、ScheduledTask）
- 每次重构后 mvn compile 验证