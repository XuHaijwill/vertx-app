package com.example.core;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/** Request Validator */
public class RequestValidator {

    public static ValidationResult validateCreateUser(JsonObject body) {
        ValidationResult result = new ValidationResult();
        if (body == null) { result.addError("body", "Request body is required"); return result; }

        String name = body.getString("name");
        if (name == null || name.trim().isEmpty()) result.addError("name", "Name is required");
        else if (name.length() < 2) result.addError("name", "Name must be at least 2 characters");
        else if (name.length() > 50) result.addError("name", "Name must be less than 50 characters");

        String email = body.getString("email");
        if (email == null || email.trim().isEmpty()) result.addError("email", "Email is required");
        else if (!isValidEmail(email)) result.addError("email", "Invalid email format");

        if (body.containsKey("age")) {
            Integer age = body.getInteger("age");
            if (age == null || age < 0 || age > 150) result.addError("age", "Age must be between 0 and 150");
        }
        return result;
    }

    public static ValidationResult validateUpdateUser(JsonObject body) {
        ValidationResult result = new ValidationResult();
        if (body == null) { result.addError("body", "Request body is required"); return result; }

        if (body.containsKey("name")) {
            String name = body.getString("name");
            if (name == null || name.trim().isEmpty()) result.addError("name", "Name cannot be empty");
            else if (name.length() < 2 || name.length() > 50) result.addError("name", "Name must be 2-50 characters");
        }

        if (body.containsKey("email")) {
            String email = body.getString("email");
            if (email == null || email.trim().isEmpty()) result.addError("email", "Email cannot be empty");
            else if (!isValidEmail(email)) result.addError("email", "Invalid email format");
        }

        if (body.containsKey("age")) {
            Integer age = body.getInteger("age");
            if (age == null || age < 0 || age > 150) result.addError("age", "Age must be between 0 and 150");
        }
        return result;
    }

    public static PaginationParams validatePagination(RoutingContext ctx) {
        String pageStr = ctx.queryParam("page").stream().findFirst().orElse("1");
        String sizeStr = ctx.queryParam("size").stream().findFirst().orElse("20");
        int page = 1, size = 20;
        try { page = Math.max(1, Integer.parseInt(pageStr)); } catch (NumberFormatException e) {}
        try { size = Math.min(100, Math.max(1, Integer.parseInt(sizeStr))); } catch (NumberFormatException e) {}
        return new PaginationParams(page, size);
    }

    private static boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }

    public static class ValidationResult {
        private boolean valid = true;
        private java.util.Map<String, String> errors = new java.util.HashMap<>();

        public void addError(String field, String message) { this.valid = false; this.errors.put(field, message); }
        public boolean isValid() { return valid; }
        public java.util.Map<String, String> getErrors() { return errors; }
        public io.vertx.core.json.JsonObject toJson() {
            io.vertx.core.json.JsonObject errorsJson = new io.vertx.core.json.JsonObject();
            errors.forEach(errorsJson::put);
            return new io.vertx.core.json.JsonObject().put("valid", valid).put("errors", errorsJson);
        }
    }

    public static class PaginationParams {
        public final int page, size, offset;
        public PaginationParams(int page, int size) { this.page = page; this.size = size; this.offset = (page - 1) * size; }
    }
}