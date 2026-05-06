package com.example.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;

public class User {
    private Long id;
    private String name;
    private String email;
    private Integer age;
    private String department;
    private String status;
    private BigDecimal balance;
    private Integer orderCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public User() {}

    public User(Long id, String name, String email, Integer age, String department, String status) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.age = age;
        this.department = department;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public Integer getOrderCount() { return orderCount; }
    public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /**
     * Create a User from a JSON object (e.g. request body).
     * Only writable fields are mapped; id/createdAt/updatedAt are ignored.
     */
    public static User fromJson(JsonObject json) {
        if (json == null) return null;
        User u = new User();
        if (json.containsKey("id")) u.setId(json.getLong("id"));
        u.setName(json.getString("name"));
        u.setEmail(json.getString("email"));
        u.setAge(json.getInteger("age"));
        u.setDepartment(json.getString("department"));
        u.setStatus(json.getString("status"));
        Object balanceObj = json.getValue("balance");
        u.setBalance(balanceObj != null ? new BigDecimal(balanceObj.toString()) : null);
        u.setOrderCount(json.getInteger("orderCount"));
        return u;
    }

    /**
     * Create a User from a database Row.
     */
    public static User toUser(Row row) {
        if (row == null) return null;
        User u = new User();
        u.setId(row.getLong("id"));
        u.setName(row.getString("name"));
        u.setEmail(row.getString("email"));
        u.setAge(row.getInteger("age"));
        u.setDepartment(row.getString("department"));
        u.setStatus(row.getString("status"));
        u.setBalance(row.getBigDecimal("balance"));
        u.setOrderCount(row.getInteger("order_count"));
        u.setCreatedAt(row.getLocalDateTime("created_at"));
        u.setUpdatedAt(row.getLocalDateTime("updated_at"));
        return u;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (id != null) json.put("id", id);
        if (name != null) json.put("name", name);
        if (email != null) json.put("email", email);
        if (age != null) json.put("age", age);
        if (department != null) json.put("department", department);
        if (status != null) json.put("status", status);
        if (balance != null) json.put("balance", balance.doubleValue());
        if (orderCount != null) json.put("orderCount", orderCount);
        if (createdAt != null) json.put("createdAt", createdAt.toString());
        if (updatedAt != null) json.put("updatedAt", updatedAt.toString());
        return json;
    }
}