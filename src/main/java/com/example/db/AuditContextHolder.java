package com.example.db;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * AuditContextHolder — ThreadLocal 持有当前请求的审计上下文。
 *
 * <p>类比 {@link TxContextHolder}，但绑定的是 AuditContext 而非 TransactionContext。
 *
 * <p>绑定时机：HTTP 入口（AuthHandler / BaseApi filter），整个请求链路可用。
 * 解绑时机：HTTP 响应发送前（Router 级别的 response ended 回调）。
 *
 * <p>使用示例：
 * <pre>
 * // 1. HTTP 入口（AuthHandler 或 BaseApi）
 * AuditContext ctx = new AuditContext()
 *     .setUserId(userId).setUsername(username)
 *     .setTraceId(traceId).setUserIp(ip);
 * AuditContextHolder.bind(ctx);
 *
 * // 2. Service / Repository 层直接取用（无需传参）
 * AuditLogger logger = new AuditLogger(vertx);
 * logger.log(AuditAction.AUDIT_CREATE, "orders", orderId, null, orderData);
 *
 * // 3. HTTP 响应前解绑
 * AuditContextHolder.unbind();
 * </pre>
 *
 * @see AuditContext
 * @see AuditLogger
 */
public class AuditContextHolder {

    private static final String AUDIT_KEY = "vertx.audit.context";

    // ThreadLocal fallback for non-Vert.x threads
    private static final ThreadLocal<AuditContext> FALLBACK = new ThreadLocal<>();

    private AuditContextHolder() {}

    /**
     * 绑定审计上下文到当前 Vert.x Context。
     * 如果不在 Vert.x 线程中，fallback 到 ThreadLocal。
     */
    public static void bind(AuditContext ctx) {
        Context vertxContext = Vertx.currentContext();
        if (vertxContext != null) {
            vertxContext.put(AUDIT_KEY, ctx);
        } else {
            FALLBACK.set(ctx);
        }
    }

    /**
     * 返回当前绑定的审计上下文，可能为 null（未鉴权或未设置）。
     */
    public static AuditContext current() {
        Context vertxContext = Vertx.currentContext();
        if (vertxContext != null) {
            return (AuditContext) vertxContext.get(AUDIT_KEY);
        }
        return FALLBACK.get();
    }

    /**
     * 返回当前上下文，如果未绑定则创建一个新的"空"上下文。
     * 用于 Service 层在无法保证上层已绑定时兜底。
     */
    public static AuditContext currentOrNew() {
        AuditContext ctx = current();
        if (ctx == null) {
            ctx = new AuditContext();
        }
        return ctx;
    }

    /**
     * 检查是否已绑定上下文。
     */
    public static boolean isActive() {
        return current() != null;
    }

    /**
     * 解绑并返回已解绑的上下文（用于日志记录返回值等场景）。
     * 推荐在 HTTP 响应发送前调用。
     */
    public static AuditContext unbind() {
        Context vertxContext = Vertx.currentContext();
        if (vertxContext != null) {
            AuditContext ctx = (AuditContext) vertxContext.get(AUDIT_KEY);
            vertxContext.remove(AUDIT_KEY);
            return ctx;
        }
        AuditContext ctx = FALLBACK.get();
        FALLBACK.remove();
        return ctx;
    }

    /**
     * 安全解绑 — 如果当前没有绑定上下文也不会报错。
     */
    public static void unbindIfPresent() {
        unbind();
    }

    /**
     * 用当前上下文中的值填充一个 JsonObject（用于 AuditLog record）。
     * 如果没有上下文，返回一个填充了默认值的对象。
     */
    public static JsonObject toLogRecord(String traceId) {
        AuditContext ctx = current();
        JsonObject record = new JsonObject();
        if (ctx != null) {
            record.put("traceId", ctx.getTraceId())
                  .put("userId", ctx.getUserId())
                  .put("username", ctx.getUsername())
                  .put("userIp", ctx.getUserIp())
                  .put("userAgent", ctx.getUserAgent())
                  .put("serviceName", ctx.getServiceName());
        } else {
            record.put("traceId", traceId != null ? traceId : "N/A")
                  .put("serviceName", "vertx-app");
        }
        return record;
    }
}
