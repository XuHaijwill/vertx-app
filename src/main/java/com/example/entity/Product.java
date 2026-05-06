package com.example.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import io.vertx.core.json.JsonObject;

public class Product {
    private Long id;
    private String name;
    private String category;
    private BigDecimal price;
    private Integer stock;
    private String description;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Product() {}

    public Product(Long id, String name, BigDecimal price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /**
     * Create a Product from a JSON object (e.g. request body).
     * Only writable fields are mapped; id/createdAt/updatedAt are ignored.
     */
    public static Product fromJson(JsonObject json) {
        if (json == null) return null;
        Product p = new Product();
        if (json.containsKey("id")) p.setId(json.getLong("id"));
        p.setName(json.getString("name"));
        p.setCategory(json.getString("category"));
        Object priceObj = json.getValue("price");
        p.setPrice(priceObj != null ? new BigDecimal(priceObj.toString()) : null);
        p.setStock(json.getInteger("stock"));
        p.setDescription(json.getString("description"));
        p.setStatus(json.getString("status"));
        return p;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (id != null) json.put("id", id);
        if (name != null) json.put("name", name);
        if (category != null) json.put("category", category);
        if (price != null) json.put("price", price.doubleValue());
        if (stock != null) json.put("stock", stock);
        if (description != null) json.put("description", description);
        if (status != null) json.put("status", status);
        if (createdAt != null) json.put("createdAt", createdAt.toString());
        if (updatedAt != null) json.put("updatedAt", updatedAt.toString());
        return json;
    }
}