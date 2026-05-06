package com.example.entity;

import java.time.LocalDateTime;
import io.vertx.core.json.JsonObject;

public class InventoryTransaction {
    private Long id;
    private Long productId;
    private Long orderId;
    private String type;
    private Integer delta;
    private Integer stockBefore;
    private Integer stockAfter;
    private String reason;
    private Long operatorId;
    private LocalDateTime createdAt;

    public InventoryTransaction() {}

    public InventoryTransaction(Long id, Long productId, String type, Integer delta, Integer stockBefore, Integer stockAfter) {
        this.id = id;
        this.productId = productId;
        this.type = type;
        this.delta = delta;
        this.stockBefore = stockBefore;
        this.stockAfter = stockAfter;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Integer getDelta() { return delta; }
    public void setDelta(Integer delta) { this.delta = delta; }

    public Integer getStockBefore() { return stockBefore; }
    public void setStockBefore(Integer stockBefore) { this.stockBefore = stockBefore; }

    public Integer getStockAfter() { return stockAfter; }
    public void setStockAfter(Integer stockAfter) { this.stockAfter = stockAfter; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    // Transient field from JOIN
    private String productName;
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (id != null) json.put("id", id);
        if (productId != null) json.put("productId", productId);
        if (orderId != null) json.put("orderId", orderId);
        if (type != null) json.put("type", type);
        if (delta != null) json.put("delta", delta);
        if (stockBefore != null) json.put("stockBefore", stockBefore);
        if (stockAfter != null) json.put("stockAfter", stockAfter);
        if (reason != null) json.put("reason", reason);
        if (operatorId != null) json.put("operatorId", operatorId);
        if (createdAt != null) json.put("createdAt", createdAt.toString());
        if (productName != null) json.put("productName", productName);
        return json;
    }
}