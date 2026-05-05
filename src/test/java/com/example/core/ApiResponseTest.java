package com.example.core;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    @BeforeEach
    void resetKeys() {
        // Reset custom key mapping to defaults before each test
        ApiResponse.configure(new JsonObject()
            .put("response", new JsonObject()
                .put("keys", new JsonObject()
                    .put("code", "code")
                    .put("message", "message"))));
    }

    @Test
    void success_noData() {
        ApiResponse resp = ApiResponse.success();
        JsonObject json = resp.toJson();
        assertEquals("success", json.getString("code"));
        assertEquals("Success", json.getString("message"));
        assertNull(json.getValue("data"));
        assertTrue(json.getLong("timestamp") > 0);
    }

    @Test
    void success_withData() {
        JsonObject data = new JsonObject().put("id", 1).put("name", "test");
        ApiResponse resp = ApiResponse.success(data);
        JsonObject json = resp.toJson();
        assertEquals("success", json.getString("code"));
        assertEquals(data, json.getJsonObject("data"));
    }

    @Test
    void success_customMessage() {
        JsonObject data = new JsonObject().put("count", 5);
        ApiResponse resp = ApiResponse.success("items found", data);
        assertEquals("items found", resp.getMessage());
    }

    @Test
    void error_simple() {
        ApiResponse resp = ApiResponse.error("something failed");
        assertEquals("error", resp.getCode());
        assertEquals("something failed", resp.getMessage());
    }

    @Test
    void error_withCode() {
        ApiResponse resp = ApiResponse.error("NOT_FOUND", "User not found");
        assertEquals("NOT_FOUND", resp.getCode());
        assertEquals("User not found", resp.getMessage());
    }

    @Test
    void fail_simple() {
        ApiResponse resp = ApiResponse.fail("validation failed");
        assertEquals("fail", resp.getCode());
        assertEquals("validation failed", resp.getMessage());
    }

    @Test
    void page_normalCase() {
        JsonObject list = new JsonObject().put("item", "test");
        ApiResponse resp = ApiResponse.page(list, 55, 3, 10);
        JsonObject json = resp.toJson();
        JsonObject data = json.getJsonObject("data");
        assertEquals(list, data.getJsonObject("list"));
        assertEquals(55L, data.getLong("total"));
        assertEquals(3, data.getInteger("page"));
        assertEquals(10, data.getInteger("size"));
        assertEquals(6, data.getInteger("pages")); // ceil(55/10) = 6
    }

    @Test
    void page_exactDivision() {
        ApiResponse resp = ApiResponse.page(new JsonObject(), 100, 5, 20);
        JsonObject data = resp.toJson().getJsonObject("data");
        assertEquals(5, data.getInteger("pages")); // 100/20 = 5
    }

    @Test
    void putExtra_singleField() {
        JsonObject data = new JsonObject().put("id", 1);
        ApiResponse resp = ApiResponse.success(data)
            .putExtra("permission", "admin");
        JsonObject json = resp.toJson();
        assertEquals("admin", json.getString("permission"));
        assertEquals(data, json.getJsonObject("data"));
    }

    @Test
    void putExtra_multipleFields() {
        ApiResponse resp = ApiResponse.success("ok")
            .putExtras("role", "user", "count", 42, "flag", true);
        JsonObject json = resp.toJson();
        assertEquals("user", json.getString("role"));
        assertEquals(42, json.getInteger("count"));
        assertEquals(true, json.getValue("flag"));
    }

    @Test
    void putExtra_nullKey_ignored() {
        ApiResponse resp = ApiResponse.success().putExtra(null, "val");
        JsonObject json = resp.toJson();
        assertFalse(json.containsKey("null"));
    }

    @Test
    void putExtra_emptyKey_ignored() {
        ApiResponse resp = ApiResponse.success().putExtra("", "val");
        JsonObject json = resp.toJson();
        assertFalse(json.containsKey(""));
    }

    @Test
    void withStatus_storesHttpStatus() {
        ApiResponse resp = ApiResponse.error("forbidden").withStatus(403);
        assertEquals(403, resp.getHttpStatus());
    }

    @Test
    void duration_notIncludedWhenZero() {
        ApiResponse resp = ApiResponse.success();
        JsonObject json = resp.toJson();
        assertNull(json.getValue("duration"));
    }

    @Test
    void duration_includedWhenPositive() {
        ApiResponse resp = ApiResponse.success().setDuration(150);
        JsonObject json = resp.toJson();
        assertEquals(150L, json.getLong("duration"));
    }

    @Test
    void configure_customKeys() {
        JsonObject config = new JsonObject()
            .put("response", new JsonObject()
                .put("keys", new JsonObject()
                    .put("code", "status")
                    .put("message", "msg")));
        ApiResponse.configure(config);

        // After configure, new responses should use custom keys
        ApiResponse resp = ApiResponse.success("hello", null);
        JsonObject json = resp.toJson();
        assertEquals("success", json.getString("status"));
        assertEquals("hello", json.getString("msg"));
        assertFalse(json.containsKey("code"));
        assertFalse(json.containsKey("message"));
    }

    @Test
    void setters_chainable() {
        ApiResponse resp = new ApiResponse()
            .setCode("custom")
            .setMessage("test")
            .setData(42)
            .setTimestamp(1000L)
            .setDuration(50L);

        assertEquals("custom", resp.getCode());
        assertEquals("test", resp.getMessage());
        assertEquals(42, resp.getData());
        assertEquals(1000L, resp.getTimestamp());
        assertEquals(50L, resp.getDuration());
    }
}
