package com.example.service;

import io.vertx.core.Future;
import com.example.core.PageResult;
import com.example.entity.Product;
import java.util.List;

/** Product Service Interface */
public interface ProductService {

    Future<List<Product>> findAll();
    Future<Product> findById(Long id);
    Future<List<Product>> search(String keyword, String category);
    Future<PageResult<Product>> findPaginated(int page, int size);
    Future<PageResult<Product>> searchPaginated(String keyword, String category, int page, int size);
    Future<Product> create(Product product);
    Future<Product> update(Long id, Product product);
    Future<Void> delete(Long id);
    Future<List<Product>> batchCreate(List<Product> products);
    Future<List<Product>> batchUpdate(List<Product> products);
    Future<Integer> batchDelete(List<Long> ids);
}
