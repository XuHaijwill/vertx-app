package com.example.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Product Service Interface - 产品服务接口
 */
public interface ProductService {

    /**
     * Get all products
     */
    Future<java.util.List<JsonObject>> findAll();

    /**
     * Get product by ID
     */
    Future<JsonObject> findById(Long id);

    /**
     * Search products
     */
    Future<java.util.List<JsonObject>> search(String keyword, String category);

    /**
     * Create product
     */
    Future<JsonObject> create(JsonObject product);

    /**
     * Update product
     */
    Future<JsonObject> update(Long id, JsonObject product);

    /**
     * Delete product
     */
    Future<Void> delete(Long id);
}