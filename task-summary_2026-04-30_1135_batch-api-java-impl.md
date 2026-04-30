# 任务记录 — 批量操作 API Java 实现

**时间**: 2026-04-30 11:35
**目标**: 为 Products 和 Users 添加 batch CRUD 端点的 Java 后端实现

## 修改的文件（8个）

### Repository 层
1. **ProductRepository.java** — 添加 `deleteByIds(List<Long>)` 和 `createBatch(List<JsonObject>)`
   - `deleteByIds`: 使用 `WHERE id IN (...)` 单条 SQL 删除
   - `createBatch`: 使用 `BatchOperations.multiRowInsert` 批量插入，然后 `SELECT ... WHERE id IN (...)` 返回完整行

2. **UserRepository.java** — 同上模式添加 `deleteByIds` 和 `createBatch`

### Service 层
3. **ProductService.java** — 接口添加 `batchCreate`, `batchUpdate`, `batchDelete` 返回 `Future<JsonObject>`
4. **ProductServiceImpl.java** — 实现批量方法：
   - 最大批量 100 条
   - `batchCreate`: 校验 name/price，调用 repo.createBatch
   - `batchUpdate`: 串行逐条更新，收集成功/失败
   - `batchDelete`: 调用 repo.deleteByIds
   - Demo 模式 fallback 完整实现

5. **UserService.java** — 接口添加 3 个批量方法
6. **UserServiceImpl.java** — 同上模式实现

### API 层
7. **ProductApi.java** — 注册 3 条 batch 路由：
   - `POST /api/products/batch` — body: `{"items": [...]}`
   - `PUT /api/products/batch` — body: `{"items": [{id, ...}, ...]}`
   - `DELETE /api/products/batch` — body: `{"ids": [1, 2, 3]}`

8. **UserApi.java** — 同上模式注册 3 条 batch 路由

## 响应格式

### batchCreate / batchUpdate
```json
{
  "code": "SUCCESS",
  "data": {
    "created": 3,
    "failed": 0,
    "items": [...]
  }
}
```

### batchUpdate (有失败时)
```json
{
  "code": "SUCCESS",
  "data": {
    "updated": 2,
    "failed": 1,
    "items": [...],
    "failedItems": [...]
  }
}
```

### batchDelete
```json
{
  "code": "SUCCESS",
  "data": {
    "deleted": 3,
    "failed": 0
  }
}
```

## 编译状态
✅ `mvn compile` 通过，零错误
