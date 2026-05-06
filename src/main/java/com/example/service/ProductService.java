package com.example.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import com.example.core.PageResult;
import com.example.entity.Product;
import java.util.List;

/** Product Service Interface */
public interface ProductService {

    Future<List<JsonObject>> findAll();
    Future<JsonObject> findById(Long id);
    Future<List<JsonObject>> search(String keyword, String category);
    Future<PageResult<JsonObject>> findPaginated(int page, int size);
    Future<PageResult<JsonObject>> searchPaginated(String keyword, String category, int page, int size);
    Future<JsonObject> create(Product product);
    Future<JsonObject> update(Long id, Product product);
    Future<Void> delete(Long id);
    Future<JsonObject> batchCreate(List<Product> products);
    Future<JsonObject> batchUpdate(List<Product> products);
    Future<JsonObject> batchDelete(List<Long> ids);
}