package com.example.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import io.vertx.core.json.JsonObject;

public class Payment {
    private Long id;
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private String method;
    private String status;
    private String transactionNo;
    private String remark;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // Transient fields from JOINs
    private BigDecimal orderTotal;
    private String orderStatus;
    private String userName;

    public Payment() {}

    public Payment(Long id, Long orderId, BigDecimal amount, String status) {
        this.id = id;
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTransactionNo() { return transactionNo; }
    public void setTransactionNo(String transactionNo) { this.transactionNo = transactionNo; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public BigDecimal getOrderTotal() { return orderTotal; }
    public void setOrderTotal(BigDecimal orderTotal) { this.orderTotal = orderTotal; }

    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (id != null) json.put("id", id);
        if (orderId != null) json.put("orderId", orderId);
        if (userId != null) json.put("userId", userId);
        if (amount != null) json.put("amount", amount.doubleValue());
        if (method != null) json.put("method", method);
        if (status != null) json.put("status", status);
        if (transactionNo != null) json.put("transactionNo", transactionNo);
        if (remark != null) json.put("remark", remark);
        if (completedAt != null) json.put("completedAt", completedAt.toString());
        if (createdAt != null) json.put("createdAt", createdAt.toString());
        if (updatedAt != null) json.put("updatedAt", updatedAt.toString());
        if (orderTotal != null) json.put("orderTotal", orderTotal.doubleValue());
        if (orderStatus != null) json.put("orderStatus", orderStatus);
        if (userName != null) json.put("userName", userName);
        return json;
    }
}