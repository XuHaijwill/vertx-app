package com.example.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import io.vertx.core.json.JsonObject;

public class Order {
    private Long id;
    private Long userId;
    private String status;
    private BigDecimal total;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // Transient fields from JOINs
    private String userName;
    private List<OrderItem> items;

    public Order() {}

    public Order(Long id, Long userId, String status, BigDecimal total) {
        this.id = id;
        this.userId = userId;
        this.status = status;
        this.total = total;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (id != null) json.put("id", id);
        if (userId != null) json.put("userId", userId);
        if (status != null) json.put("status", status);
        if (total != null) json.put("total", total.doubleValue());
        if (remark != null) json.put("remark", remark);
        if (createdAt != null) json.put("createdAt", createdAt.toString());
        if (updatedAt != null) json.put("updatedAt", updatedAt.toString());
        if (userName != null) json.put("userName", userName);
        if (items != null) {
            json.put("items", items.stream().map(OrderItem::toJson).collect(java.util.stream.Collectors.toList()));
        }
        return json;
    }
}