package com.example.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import io.vertx.core.json.JsonObject;

public class OrderItem {
    private Long id;
    private Long orderId;
    private Long productId;
    private Integer quantity;
    private BigDecimal price;
    private LocalDateTime createdAt;
    // Transient field from JOIN
    private String productName;

    public OrderItem() {}

    public OrderItem(Long id, Long orderId, Long productId, Integer quantity, BigDecimal price) {
        this.id = id;
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.price = price;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (id != null) json.put("id", id);
        if (orderId != null) json.put("orderId", orderId);
        if (productId != null) json.put("productId", productId);
        if (quantity != null) json.put("quantity", quantity);
        if (price != null) json.put("price", price.doubleValue());
        if (createdAt != null) json.put("createdAt", createdAt.toString());
        if (productName != null) json.put("productName", productName);
        return json;
    }
}