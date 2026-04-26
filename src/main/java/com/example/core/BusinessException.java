package com.example.core;

/**
 * Business Exception - 业务异常
 */
public class BusinessException extends RuntimeException {

    private String code = "BUSINESS_ERROR";
    private int httpStatus = 400;

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }

    // ========== Common Exceptions ==========

    public static BusinessException notFound(String resource) {
        return new BusinessException("NOT_FOUND", resource + " not found", 404);
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException("BAD_REQUEST", message, 400);
    }

    public static BusinessException unauthorized() {
        return new BusinessException("UNAUTHORIZED", "Unauthorized access", 401);
    }

    public static BusinessException forbidden() {
        return new BusinessException("FORBIDDEN", "Access forbidden", 403);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException("CONFLICT", message, 409);
    }

    public static BusinessException serverError(String message) {
        return new BusinessException("SERVER_ERROR", message, 500);
    }

    // ========== Getters ==========

    public String getCode() { return code; }
    public int getHttpStatus() { return httpStatus; }
}