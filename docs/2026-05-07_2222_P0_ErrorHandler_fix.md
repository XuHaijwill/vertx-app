# P0: ErrorHandler 死代码 + 错误格式不统一 — 修复完成

## 问题描述

1. `ErrorHandler.java` 存在但 MainVerticle 从未调用它，MainVerticle.addErrorHandlers() 用内联 lambda 替代
2. ErrorHandler 写原始 JSON 字符串（`{"error":"...","message":"..."}`），与 BaseApi.fail()/ApiResponse 格式不一致

## 修复内容

### 1. ErrorHandler.java — 全面重构

- 所有方法改用 `ApiResponse` 包装输出（统一的 `{code, message, detail, timestamp}` 结构）
- `notFound()` — 404，响应含 `detail: "Endpoint not found: ..."`
- `internalError()` — 500，**服务端打印完整堆栈，客户端只收到安全消息**（dev 模式才暴露 detail）
- `badRequest()` — 400，支持自定义消息
- `unauthorized()` — 401，固定 "Authentication required"
- `forbidden()` — 403，固定 "Access denied"

### 2. MainVerticle.java — 改用 ErrorHandler

```java
// Before（内联 lambda，未使用 ErrorHandler）
router.errorHandler(404, ctx ->
    ctx.json(ApiResponse.error("NOT_FOUND", "Endpoint not found: " + ctx.request().path()).toJson()));
router.errorHandler(500, ctx -> {
    LOG.error("500 Error", ctx.failure());
    ctx.json(ApiResponse.error("INTERNAL_ERROR", "Internal server error").toJson());
});

// After（统一调用 ErrorHandler）
router.errorHandler(404, ErrorHandler::notFound);
router.errorHandler(500, ErrorHandler::internalError);
```

## 关键设计决策

- 500 错误**不向客户端暴露堆栈**（防止信息泄露），通过 `SAFE_MESSAGES` 机制在 dev 模式放开
- `SAFE_MESSAGES` 检测 JVM 系统属性 `vertx.profile=dev|development`
- 所有错误响应结构一致：客户端始终收到 `{code, message, detail, timestamp}`

## 编译验证

✅ BUILD SUCCESS (mvn compile -DskipTests)
