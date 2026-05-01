package com.example.db;

import io.vertx.core.json.JsonObject;
import java.util.UUID;

/**
 * AuditContext — 审计日志的上下文信息载体。
 *
 * <p>在每次 HTTP 请求入口（如 AuthHandler 鉴权成功后）创建，
 * 通过 {@link AuditContextHolder} 绑定到当前线程（Vert.x Context），
 * 整个请求链路上的所有 Service/Repository 共享同一份上下文。
 *
 * <p>来源优先级：
 * <ul>
 *   <li>userId / username — JWT token 中的 principal</li>
 *   <li>traceId — OpenTelemetry / Skywalking trace ID，或请求头 X-Trace-Id</li>
 *   <li>requestId — Vert.x request ID（request.headers().get("X-Request-Id")）</li>
 *   <li>userIp — X-Forwarded-For 优先，其次 X-Real-IP，最后 remoteAddress()</li>
 *   <li>userAgent — User-Agent 请求头</li>
 * </ul>
 *
 * <p>使用示例（AuthHandler 鉴权成功后）：
 * <pre>
 * AuditContext ctx = new AuditContext()
 *     .setUserId(userId)
 *     .setUsername(username)
 *     .setTraceId(traceId)
 *     .setRequestId(requestId)
 *     .setUserIp(remoteIp)
 *     .setUserAgent(userAgent);
 * AuditContextHolder.bind(ctx);
 * </pre>
 *
 * @see AuditContextHolder
 * @see AuditLogger
 */
public class AuditContext {

    /** 操作用户 ID（来自 JWT subject） */
    private Long userId;

    /** 操作用户名（来自 JWT claim "preferred_username"） */
    private String username;

    /** 分布式追踪 ID（Skywalking / Jaeger / OpenTelemetry） */
    private String traceId;

    /** HTTP 请求 ID（X-Request-Id） */
    private String requestId;

    /** 客户端 IP（支持 X-Forwarded-For 代理链） */
    private String userIp;

    /** 浏览器/客户端 User-Agent */
    private String userAgent;

    /** 服务名（默认 vertx-app） */
    private String serviceName = "vertx-app";

    /** 业务扩展字段（订单金额、操作原因等） */
    private JsonObject extra = new JsonObject();

    // ---- Getters & Setters ----

    public Long getUserId() {
        return userId;
    }

    public AuditContext setUserId(Long userId) {
        this.userId = userId;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public AuditContext setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getTraceId() {
        return traceId;
    }

    /**
     * 生成并设置追踪 ID（如果未传入）。
     * 优先使用请求头 X-Trace-Id，其次 X-Request-Id，最后生成 UUID。
     */
    public AuditContext setOrGenerateTraceId(String headerTraceId, String headerRequestId) {
        if (traceId == null) {
            this.traceId = (headerTraceId != null && !headerTraceId.isBlank())
                ? headerTraceId
                : (headerRequestId != null && !headerRequestId.isBlank())
                    ? headerRequestId
                    : UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        }
        return this;
    }

    public AuditContext setTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    public String getRequestId() {
        return requestId;
    }

    public AuditContext setRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }

    public String getUserIp() {
        return userIp;
    }

    /**
     * 设置客户端 IP，优先使用 X-Forwarded-For 头（取最左侧真实 IP）。
     *
     * <p>示例：X-Forwarded-For: 192.168.1.1, 10.0.0.1 → 取 192.168.1.1
     */
    public AuditContext setUserIpFromHeader(String forwardedFor, String realIp, String remoteAddr) {
        String ip = null;
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // 取最左侧第一个 IP（最接近客户端）
            int comma = forwardedFor.indexOf(',');
            ip = comma > 0 ? forwardedFor.substring(0, comma).trim() : forwardedFor.trim();
        } else if (realIp != null && !realIp.isBlank()) {
            ip = realIp.trim();
        } else if (remoteAddr != null) {
            ip = remoteAddr;  // 可能是 "0:0:0:0:0:0:0:1" 或 "127.0.0.1"
        }
        this.userIp = ip;
        return this;
    }

    public AuditContext setUserIp(String userIp) {
        this.userIp = userIp;
        return this;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public AuditContext setUserAgent(String userAgent) {
        this.userAgent = (userAgent != null && userAgent.length() > 512)
            ? userAgent.substring(0, 512)  // 截断超长 UA
            : userAgent;
        return this;
    }

    public String getServiceName() {
        return serviceName;
    }

    public AuditContext setServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    public JsonObject getExtra() {
        return extra;
    }

    public AuditContext setExtra(JsonObject extra) {
        this.extra = extra != null ? extra : new JsonObject();
        return this;
    }

    public AuditContext putExtra(String key, Object value) {
        this.extra.put(key, value);
        return this;
    }

    /**
     * 返回是否有有效用户（已登录）。
     */
    public boolean isAuthenticated() {
        return userId != null || (username != null && !username.isBlank());
    }

    @Override
    public String toString() {
        return "AuditContext{userId=" + userId
            + ", username=" + username
            + ", traceId=" + traceId
            + ", requestId=" + requestId
            + ", userIp=" + userIp
            + ", serviceName=" + serviceName
            + "}";
    }
}
