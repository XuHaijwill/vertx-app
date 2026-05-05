package com.example.core;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestValidatorTest {

    // ========== validateCreateUser ==========

    @Test
    void validateCreateUser_validInput() {
        JsonObject body = new JsonObject()
            .put("name", "John Doe")
            .put("email", "john@example.com")
            .put("age", 30);
        RequestValidator.ValidationResult result = RequestValidator.validateCreateUser(body);
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void validateCreateUser_nullBody() {
        RequestValidator.ValidationResult result = RequestValidator.validateCreateUser(null);
        assertFalse(result.isValid());
        assertEquals("Request body is required", result.getErrors().get("body"));
    }

    @Test
    void validateCreateUser_missingName() {
        JsonObject body = new JsonObject().put("email", "a@b.com");
        RequestValidator.ValidationResult result = RequestValidator.validateCreateUser(body);
        assertFalse(result.isValid());
        assertEquals("Name is required", result.getErrors().get("name"));
    }

    @Test
    void validateCreateUser_nameTooShort() {
        JsonObject body = new JsonObject().put("name", "A").put("email", "a@b.com");
        RequestValidator.ValidationResult result = RequestValidator.validateCreateUser(body);
        assertFalse(result.isValid());
        assertEquals("Name must be at least 2 characters", result.getErrors().get("name"));
    }

    @Test
    void validateCreateUser_nameTooLong() {
        String longName = "A".repeat(51);
        JsonObject body = new JsonObject().put("name", longName).put("email", "a@b.com");
        RequestValidator.ValidationResult result = RequestValidator.validateCreateUser(body);
        assertFalse(result.isValid());
        assertEquals("Name must be less than 50 characters", result.getErrors().get("name"));
    }

    @Test
    void validateCreateUser_blankName() {
        JsonObject body = new JsonObject().put("name", "   ").put("email", "a@b.com");
        RequestValidator.ValidationResult result = RequestValidator.validateCreateUser(body);
        assertFalse(result.isValid());
        assertEquals("Name is required", result.getErrors().get("name"));
    }

    @Test
    void validateCreateUser_missingEmail() {
        JsonObject body = new JsonObject().put("name", "Valid Name");
        RequestValidator.ValidationResult result = RequestValidator.validateCreateUser(body);
        assertFalse(result.isValid());
        assertEquals("Email is required", result.getErrors().get("email"));
    }

    @Test
    void validateCreateUser_invalidEmail() {
        JsonObject body = new JsonObject().put("name", "Valid Name").put("email", "not-an-email");
        RequestValidator.ValidationResult result = RequestValidator.validateCreateUser(body);
        assertFalse(result.isValid());
        assertEquals("Invalid email format", result.getErrors().get("email"));
    }

    @Test
    void validateCreateUser_ageNegative() {
        JsonObject body = new JsonObject().put("name", "Test").put("email", "t@t.com").put("age", -1);
        RequestValidator.ValidationResult result = RequestValidator.validateCreateUser(body);
        assertFalse(result.isValid());
        assertEquals("Age must be between 0 and 150", result.getErrors().get("age"));
    }

    @Test
    void validateCreateUser_ageTooLarge() {
        JsonObject body = new JsonObject().put("name", "Test").put("email", "t@t.com").put("age", 151);
        RequestValidator.ValidationResult result = RequestValidator.validateCreateUser(body);
        assertFalse(result.isValid());
        assertEquals("Age must be between 0 and 150", result.getErrors().get("age"));
    }

    @Test
    void validateCreateUser_ageValidBoundaries() {
        // age=0 should be valid
        JsonObject body0 = new JsonObject().put("name", "Test").put("email", "t@t.com").put("age", 0);
        assertTrue(RequestValidator.validateCreateUser(body0).isValid());

        // age=150 should be valid
        JsonObject body150 = new JsonObject().put("name", "Test").put("email", "t@t.com").put("age", 150);
        assertTrue(RequestValidator.validateCreateUser(body150).isValid());
    }

    @Test
    void validateCreateUser_noAge_isValid() {
        JsonObject body = new JsonObject().put("name", "Test").put("email", "t@t.com");
        assertTrue(RequestValidator.validateCreateUser(body).isValid());
    }

    // ========== validateUpdateUser ==========

    @Test
    void validateUpdateUser_validFullUpdate() {
        JsonObject body = new JsonObject()
            .put("name", "Updated")
            .put("email", "upd@test.com")
            .put("age", 25);
        assertTrue(RequestValidator.validateUpdateUser(body).isValid());
    }

    @Test
    void validateUpdateUser_emptyBody_valid() {
        // Partial update: body present but no known fields = valid (nothing to validate)
        JsonObject body = new JsonObject().put("unknown", "field");
        assertTrue(RequestValidator.validateUpdateUser(body).isValid());
    }

    @Test
    void validateUpdateUser_invalidName() {
        JsonObject body = new JsonObject().put("name", "A");
        RequestValidator.ValidationResult result = RequestValidator.validateUpdateUser(body);
        assertFalse(result.isValid());
        assertEquals("Name must be 2-50 characters", result.getErrors().get("name"));
    }

    @Test
    void validateUpdateUser_invalidEmail() {
        JsonObject body = new JsonObject().put("email", "bad");
        RequestValidator.ValidationResult result = RequestValidator.validateUpdateUser(body);
        assertFalse(result.isValid());
        assertEquals("Invalid email format", result.getErrors().get("email"));
    }

    // ========== validatePagination ==========

    @Test
    void validatePagination_defaults() {
        RoutingContext ctx = mock(RoutingContext.class);
        when(ctx.queryParam("page")).thenReturn(List.of());
        when(ctx.queryParam("size")).thenReturn(List.of());
        RequestValidator.PaginationParams params = RequestValidator.validatePagination(ctx);
        assertEquals(1, params.page);
        assertEquals(20, params.size);
        assertEquals(0, params.offset);
    }

    @Test
    void validatePagination_customValues() {
        RoutingContext ctx = mock(RoutingContext.class);
        when(ctx.queryParam("page")).thenReturn(List.of("3"));
        when(ctx.queryParam("size")).thenReturn(List.of("25"));
        RequestValidator.PaginationParams params = RequestValidator.validatePagination(ctx);
        assertEquals(3, params.page);
        assertEquals(25, params.size);
        assertEquals(50, params.offset); // (3-1)*25
    }

    @Test
    void validatePagination_clampsSize() {
        RoutingContext ctx = mock(RoutingContext.class);
        when(ctx.queryParam("page")).thenReturn(List.of("1"));
        when(ctx.queryParam("size")).thenReturn(List.of("200"));
        RequestValidator.PaginationParams params = RequestValidator.validatePagination(ctx);
        assertEquals(100, params.size); // max 100
    }

    @Test
    void validatePagination_pageMinimum1() {
        RoutingContext ctx = mock(RoutingContext.class);
        when(ctx.queryParam("page")).thenReturn(List.of("0"));
        when(ctx.queryParam("size")).thenReturn(List.of("10"));
        RequestValidator.PaginationParams params = RequestValidator.validatePagination(ctx);
        assertEquals(1, params.page); // clamped to 1
    }

    @Test
    void validatePagination_invalidStrings_useDefaults() {
        RoutingContext ctx = mock(RoutingContext.class);
        when(ctx.queryParam("page")).thenReturn(List.of("abc"));
        when(ctx.queryParam("size")).thenReturn(List.of("xyz"));
        RequestValidator.PaginationParams params = RequestValidator.validatePagination(ctx);
        assertEquals(1, params.page);
        assertEquals(20, params.size);
    }

    // ========== ValidationResult ==========

    @Test
    void validationResult_toJson() {
        RequestValidator.ValidationResult result = RequestValidator.validateCreateUser(null);
        JsonObject json = result.toJson();
        assertFalse(json.getBoolean("valid"));
        assertTrue(json.containsKey("errors"));
        assertEquals("Request body is required", json.getJsonObject("errors").getString("body"));
    }

    @Test
    void validationResult_multipleErrors() {
        JsonObject body = new JsonObject(); // missing both name and email
        RequestValidator.ValidationResult result = RequestValidator.validateCreateUser(body);
        assertEquals(2, result.getErrors().size());
        assertTrue(result.getErrors().containsKey("name"));
        assertTrue(result.getErrors().containsKey("email"));
    }
}
