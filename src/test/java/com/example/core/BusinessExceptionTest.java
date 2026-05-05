package com.example.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BusinessExceptionTest {

    @Test
    void defaultConstructor_hasDefaultCodeAndStatus() {
        BusinessException ex = new BusinessException("test error");
        assertEquals("BUSINESS_ERROR", ex.getCode());
        assertEquals(400, ex.getHttpStatus());
        assertEquals("test error", ex.getMessage());
    }

    @Test
    void customCodeConstructor() {
        BusinessException ex = new BusinessException("CUSTOM", "something went wrong");
        assertEquals("CUSTOM", ex.getCode());
        assertEquals(400, ex.getHttpStatus());
    }

    @Test
    void fullConstructor() {
        BusinessException ex = new BusinessException("FULL", "detailed", 422);
        assertEquals(422, ex.getHttpStatus());
    }

    @Test
    void causeConstructor() {
        RuntimeException cause = new RuntimeException("root cause");
        BusinessException ex = new BusinessException("wrapper", cause);
        assertSame(cause, ex.getCause());
    }

    @Test
    void notFound_factoryMethod() {
        BusinessException ex = BusinessException.notFound("User");
        assertEquals("NOT_FOUND", ex.getCode());
        assertEquals(404, ex.getHttpStatus());
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void badRequest_factoryMethod() {
        BusinessException ex = BusinessException.badRequest("invalid input");
        assertEquals("BAD_REQUEST", ex.getCode());
        assertEquals(400, ex.getHttpStatus());
    }

    @Test
    void unauthorized_factoryMethod() {
        BusinessException ex = BusinessException.unauthorized();
        assertEquals("UNAUTHORIZED", ex.getCode());
        assertEquals(401, ex.getHttpStatus());
    }

    @Test
    void forbidden_factoryMethod() {
        BusinessException ex = BusinessException.forbidden();
        assertEquals("FORBIDDEN", ex.getCode());
        assertEquals(403, ex.getHttpStatus());
    }

    @Test
    void conflict_factoryMethod() {
        BusinessException ex = BusinessException.conflict("duplicate email");
        assertEquals("CONFLICT", ex.getCode());
        assertEquals(409, ex.getHttpStatus());
    }

    @Test
    void serverError_factoryMethod() {
        BusinessException ex = BusinessException.serverError("internal failure");
        assertEquals("SERVER_ERROR", ex.getCode());
        assertEquals(500, ex.getHttpStatus());
    }
}
