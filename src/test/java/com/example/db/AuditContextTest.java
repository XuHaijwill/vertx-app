package com.example.db;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuditContextTest {

    @Test
    void defaultValues() {
        AuditContext ctx = new AuditContext();
        assertNull(ctx.getUserId());
        assertNull(ctx.getUsername());
        assertNull(ctx.getTraceId());
        assertNull(ctx.getRequestId());
        assertNull(ctx.getUserIp());
        assertNull(ctx.getUserAgent());
        assertEquals("vertx-app", ctx.getServiceName());
        assertNotNull(ctx.getExtra());
        assertFalse(ctx.isAuthenticated());
    }

    @Test
    void fluentSetters() {
        AuditContext ctx = new AuditContext()
            .setUserId(42L)
            .setUsername("admin")
            .setTraceId("trace-123")
            .setRequestId("req-456")
            .setUserIp("192.168.1.1")
            .setUserAgent("Mozilla/5.0")
            .setServiceName("my-service");

        assertEquals(42L, ctx.getUserId());
        assertEquals("admin", ctx.getUsername());
        assertEquals("trace-123", ctx.getTraceId());
        assertEquals("req-456", ctx.getRequestId());
        assertEquals("192.168.1.1", ctx.getUserIp());
        assertEquals("Mozilla/5.0", ctx.getUserAgent());
        assertEquals("my-service", ctx.getServiceName());
    }

    @Test
    void isAuthenticated_trueWithUserId() {
        AuditContext ctx = new AuditContext().setUserId(1L);
        assertTrue(ctx.isAuthenticated());
    }

    @Test
    void isAuthenticated_trueWithUsername() {
        AuditContext ctx = new AuditContext().setUsername("user1");
        assertTrue(ctx.isAuthenticated());
    }

    @Test
    void isAuthenticated_falseWithBlankUsername() {
        AuditContext ctx = new AuditContext().setUsername("   ");
        assertFalse(ctx.isAuthenticated());
    }

    @Test
    void setOrGenerateTraceId_usesTraceHeader() {
        AuditContext ctx = new AuditContext();
        ctx.setOrGenerateTraceId("trace-from-header", "req-123");
        assertEquals("trace-from-header", ctx.getTraceId());
    }

    @Test
    void setOrGenerateTraceId_fallsBackToRequestId() {
        AuditContext ctx = new AuditContext();
        ctx.setOrGenerateTraceId(null, "req-456");
        assertEquals("req-456", ctx.getTraceId());
    }

    @Test
    void setOrGenerateTraceId_generatesUuid() {
        AuditContext ctx = new AuditContext();
        ctx.setOrGenerateTraceId(null, null);
        assertNotNull(ctx.getTraceId());
        assertEquals(16, ctx.getTraceId().length());
    }

    @Test
    void setOrGenerateTraceId_doesNotOverwrite() {
        AuditContext ctx = new AuditContext().setTraceId("existing");
        ctx.setOrGenerateTraceId("new-trace", "new-req");
        assertEquals("existing", ctx.getTraceId());
    }

    @Test
    void setUserIpFromHeader_forwardedFor() {
        AuditContext ctx = new AuditContext();
        ctx.setUserIpFromHeader("10.0.0.1, 172.16.0.1", "1.2.3.4", "127.0.0.1");
        assertEquals("10.0.0.1", ctx.getUserIp()); // first IP
    }

    @Test
    void setUserIpFromHeader_forwardedFor_single() {
        AuditContext ctx = new AuditContext();
        ctx.setUserIpFromHeader("10.0.0.1", null, "127.0.0.1");
        assertEquals("10.0.0.1", ctx.getUserIp());
    }

    @Test
    void setUserIpFromHeader_realIp_fallback() {
        AuditContext ctx = new AuditContext();
        ctx.setUserIpFromHeader(null, "5.6.7.8", "127.0.0.1");
        assertEquals("5.6.7.8", ctx.getUserIp());
    }

    @Test
    void setUserIpFromHeader_remoteAddr_fallback() {
        AuditContext ctx = new AuditContext();
        ctx.setUserIpFromHeader(null, null, "192.168.1.100");
        assertEquals("192.168.1.100", ctx.getUserIp());
    }

    @Test
    void setUserAgent_truncatesLong() {
        String longUA = "A".repeat(1000);
        AuditContext ctx = new AuditContext().setUserAgent(longUA);
        assertEquals(512, ctx.getUserAgent().length());
    }

    @Test
    void setUserAgent_normalLength() {
        String ua = "Mozilla/5.0";
        AuditContext ctx = new AuditContext().setUserAgent(ua);
        assertEquals(ua, ctx.getUserAgent());
    }

    @Test
    void setUserAgent_nullHandled() {
        AuditContext ctx = new AuditContext().setUserAgent(null);
        assertNull(ctx.getUserAgent());
    }

    @Test
    void extra_operations() {
        AuditContext ctx = new AuditContext()
            .putExtra("orderId", 99)
            .putExtra("reason", "test");
        assertEquals(99, ctx.getExtra().getInteger("orderId"));
        assertEquals("test", ctx.getExtra().getString("reason"));
    }

    @Test
    void setExtra_nullBecomesEmpty() {
        AuditContext ctx = new AuditContext();
        ctx.setExtra(null);
        assertNotNull(ctx.getExtra());
        assertEquals(0, ctx.getExtra().size());
    }

    @Test
    void toString_containsKeyFields() {
        AuditContext ctx = new AuditContext()
            .setUserId(1L)
            .setUsername("admin")
            .setTraceId("t1");
        String str = ctx.toString();
        assertTrue(str.contains("userId=1"));
        assertTrue(str.contains("username=admin"));
        assertTrue(str.contains("traceId=t1"));
    }
}
