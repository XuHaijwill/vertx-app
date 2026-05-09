package com.example.handlers;

import com.example.db.AuditContext;
import com.example.db.AuditContextHolder;
import com.example.entity.AccessLog;
import com.example.repository.AccessLogRepository;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AccessLogHandler — 自动记录所有 /api/* 请求到 access_log 表
 *
 * <p>作为 Vert.x Route Handler 安装在路由链中，在请求完成后异步写入访问日志。
 * 写入使用独立连接，不阻塞业务请求。
 *
 * <h3>安装方式：</h3>
 * <pre>
 * router.route(contextPath + "/api/*")
 *     .handler(new AccessLogHandler(vertx));
 * </pre>
 *
 * <h3>记录字段：</h3>
 * <ul>
 *   <li>traceId — 来自 AuditContext 或 X-Request-ID</li>
 *   <li>userId / username — 来自 KeycloakAuthHandler 注入的 RoutingContext</li>
 *   <li>method / path / queryString — HTTP 请求信息</li>
 *   <li>statusCode — HTTP 响应状态码</li>
 *   <li>responseTime — 从请求进入 Handler 到响应写入的时间（ms）</li>
 *   <li>userIp / userAgent — 客户端信息</li>
 * </ul>
 */
public class AccessLogHandler implements io.vertx.core.Handler<RoutingContext> {

    private static final Logger LOG = LoggerFactory.getLogger(AccessLogHandler.class);

    private final AccessLogRepository repo;

    public AccessLogHandler(Vertx vertx) {
        this.repo = new AccessLogRepository(vertx);
    }

    @Override
    public void handle(RoutingContext ctx) {
        long startTime = System.currentTimeMillis();

        // Install a response-body-written handler to capture status code and write log
        ctx.response().bodyEndHandler(v -> {
            try {
                long responseTime = System.currentTimeMillis() - startTime;

                AccessLog log = new AccessLog();
                log.setMethod(ctx.request().method().name());
                log.setPath(ctx.request().path());
                log.setQueryString(ctx.request().query());
                log.setStatusCode(ctx.response().getStatusCode());
                log.setResponseTime((int) responseTime);

                // User info from auth handler
                Object authUser = ctx.get("authUser");
                if (authUser instanceof com.example.auth.AuthUser au) {
                    log.setUserId(au.getUserId());
                    log.setUsername(au.getUsername());
                } else {
                    // Fallback: try direct context values
                    Object uid = ctx.get("userId");
                    if (uid instanceof Long) log.setUserId((Long) uid);
                    Object uname = ctx.get("username");
                    if (uname instanceof String) log.setUsername((String) uname);
                }

                // Trace / Request ID
                AuditContext auditCtx = AuditContextHolder.current();
                if (auditCtx != null) {
                    log.setTraceId(auditCtx.getTraceId());
                    log.setRequestId(auditCtx.getRequestId());
                    log.setUserIp(auditCtx.getUserIp());
                    log.setUserAgent(auditCtx.getUserAgent());
                } else {
                    String reqId = ctx.get("requestId");
                    log.setRequestId(reqId);
                    log.setTraceId(reqId);  // Use requestId as fallback traceId

                    // IP resolution
                    String fwd = ctx.request().getHeader("X-Forwarded-For");
                    String realIp = ctx.request().getHeader("X-Real-IP");
                    String remoteAddr = ctx.request().remoteAddress() != null
                        ? ctx.request().remoteAddress().host() : null;
                    if (fwd != null && !fwd.isBlank()) {
                        log.setUserIp(fwd.split(",")[0].trim());
                    } else if (realIp != null && !realIp.isBlank()) {
                        log.setUserIp(realIp);
                    } else {
                        log.setUserIp(remoteAddr);
                    }

                    log.setUserAgent(ctx.request().getHeader("User-Agent"));
                }

                // Error message for 5xx
                if (ctx.response().getStatusCode() >= 500) {
                    Object failure = ctx.failure();
                    if (failure instanceof Throwable t) {
                        String msg = t.getMessage();
                        log.setErrorMessage(msg != null && msg.length() > 512
                            ? msg.substring(0, 512) : msg);
                    }
                }

                // Async insert — fire and forget (don't block the response)
                repo.insert(log)
                    .onFailure(err -> LOG.debug("[ACCESS-LOG] Failed to write: {}", err.getMessage()));

            } catch (Exception e) {
                // Never let access logging crash the request
                LOG.debug("[ACCESS-LOG] Error in handler: {}", e.getMessage());
            } finally {
                // Clean up AuditContext if it was bound by auth handler
                AuditContextHolder.unbind();
            }
        });

        ctx.next();
    }
}
