# API 参考

## 统一响应格式

所有 API 返回统一的 JSON 格式：

```json
{
  "code": 200,
  "msg": "success",
  "data": { ... }
}
```

分页响应：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "list": [ ... ],
    "total": 100,
    "page": 1,
    "size": 20,
    "pages": 5
  }
}
```

错误响应：

```json
{
  "code": "BAD_REQUEST",
  "msg": "参数错误: name is required"
}
```

## 健康检查

### GET /health

健康检查端点，无需认证。

## 认证

### GET /api/auth/config

获取 Keycloak 认证配置（公开）。

### GET /api/auth/me

获取当前登录用户信息（需认证）。

## 用户管理

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/users | 用户列表（分页） | - |
| GET | /api/users/:id | 用户详情 | - |
| POST | /api/users | 创建用户 | - |
| PUT | /api/users/:id | 更新用户 | - |
| DELETE | /api/users/:id | 删除用户 | - |
| POST | /api/users/batch | 批量创建 | system:user:add |
| PUT | /api/users/batch | 批量更新 | system:user:edit |
| DELETE | /api/users/batch | 批量删除 | system:user:edit |

### 查询参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | int | 1 | 页码 |
| size | int | 20 | 每页数量（最大 100） |
| username | string | - | 用户名过滤 |
| email | string | - | 邮箱过滤 |
| status | string | - | 状态过滤 |

## 商品管理

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/products | 商品列表（分页） | - |
| GET | /api/products/:id | 商品详情 | - |
| POST | /api/products | 创建商品 | - |
| PUT | /api/products/:id | 更新商品 | - |
| DELETE | /api/products/:id | 删除商品 | - |
| GET | /api/products/search | 搜索商品 | - |
| POST | /api/products/batch | 批量创建 | system:product:edit |
| PUT | /api/products/batch | 批量更新 | system:product:edit |
| DELETE | /api/products/batch | 批量删除 | system:product:edit |

### Demo 模式

数据库不可用时，返回内置示例商品（iPhone 15、MacBook Pro、Coffee Maker）。

## 订单管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/orders | 订单列表（分页） |
| GET | /api/orders/:id | 订单详情 |
| POST | /api/orders | 创建订单 |
| PUT | /api/orders/:id/cancel | 取消订单 |

### 创建订单

```json
POST /api/orders
{
  "userId": 1,
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 3, "quantity": 1 }
  ],
  "remark": "备注"
}
```

**事务特性：** 创建订单在 60 秒事务中执行，包含库存扣减和行锁。

## 支付管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/payments | 支付列表（分页） |
| GET | /api/payments/:id | 支付详情 |
| POST | /api/payments | 发起支付 |
| POST | /api/payments/:id/refund | 退款 |

### 支付流程

1. 验证订单状态（待支付）
2. 幂等性检查（已支付则返回 idempotent）
3. 确认库存 & 记录台账
4. 更新订单状态为已支付

## 系统配置

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/sys-configs | 配置列表（分页 + 过滤） |
| GET | /api/sys-configs/:id | 配置详情 |
| GET | /api/sys-configs/key/:configKey | 按 key 查询 |
| POST | /api/sys-configs | 创建配置 |
| PUT | /api/sys-configs/:id | 更新配置 |
| DELETE | /api/sys-configs/:id | 删除配置 |

### 过滤参数

| 参数 | 类型 | 说明 |
|------|------|------|
| configName | string | 配置名称 |
| configKey | string | 配置键 |
| configValue | string | 配置值 |
| configType | string | 类型（Y/N） |

## 字典管理

### 字典类型

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/dict/types | 字典类型列表 |
| GET | /api/dict/types/:dictId | 字典类型详情 |
| POST | /api/dict/types | 创建 |
| PUT | /api/dict/types/:dictId | 更新 |
| DELETE | /api/dict/types/:dictId | 删除 |
| GET | /api/dict/types/optionselect | 下拉选择 |
| DELETE | /api/dict/types/batch/:ids | 批量删除（逗号分隔） |

### 字典数据

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/dict/data | 字典数据列表 |
| GET | /api/dict/data/:dictCode | 字典数据详情 |
| GET | /api/dict/data/type/:dictType | 按类型查询 |
| POST | /api/dict/data | 创建 |
| PUT | /api/dict/data/:dictCode | 更新 |
| DELETE | /api/dict/data/:dictCode | 删除 |

## 菜单管理

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/menus | 菜单列表 | system:menu:query |
| GET | /api/menus/tree | 菜单树 | - |
| GET | /api/menus/:id | 菜单详情 | system:menu:query |
| POST | /api/menus | 创建菜单 | system:menu:add |
| PUT | /api/menus/:id | 更新菜单 | system:menu:edit |
| DELETE | /api/menus/:id | 删除菜单 | system:menu:remove |
| GET | /api/menus/visible | 可见菜单 | - |

## 定时任务

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/scheduled-tasks | 任务列表（分页） |
| GET | /api/scheduled-tasks/:id | 任务详情 |
| POST | /api/scheduled-tasks | 创建任务 |
| PUT | /api/scheduled-tasks/:id | 更新任务 |
| DELETE | /api/scheduled-tasks/:id | 删除任务 |
| POST | /api/scheduled-tasks/:id/pause | 暂停任务 |
| POST | /api/scheduled-tasks/:id/resume | 恢复任务 |
| POST | /api/scheduled-tasks/:id/trigger | 手动触发 |

## 审计日志

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/audits | 搜索审计日志（分页） |
| GET | /api/audits/entity/:type/:id | 按实体查询 |
| GET | /api/audits/user/:userId | 按用户查询 |
| GET | /api/audits/stats | 统计信息 |
| POST | /api/audits/archive | 归档旧日志 |

## 批量操作

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/batch/products | 批量商品操作 | system:product:edit |
| POST | /api/batch/users | 批量用户操作 | system:user:edit |
| POST | /api/batch/orders | 批量订单操作 | system:order:edit |

### 批量请求格式

```json
POST /api/batch/products
{
  "action": "create",
  "items": [
    { "name": "Product A", "price": 99.9 },
    { "name": "Product B", "price": 199.9 }
  ]
}
```

**限制：** 每次批量操作最多 100 条。

## 文档

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /docs | Swagger UI |
| GET | /openapi.yaml | OpenAPI 规范 |
