package com.example.service;

import com.example.entity.User;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import com.example.core.PageResult;
import java.util.List;

/** User Service Interface */
public interface UserService {

    Future<List<JsonObject>> findAll();
    Future<JsonObject> findById(Long id);
    Future<List<JsonObject>> search(String keyword);
    Future<PageResult<JsonObject>> findPaginated(int page, int size);
    Future<PageResult<JsonObject>> searchPaginated(String keyword, int page, int size);
    Future<JsonObject> create(User user);
    Future<JsonObject> update(Long id, User user);
    Future<Void> delete(Long id);
    Future<Boolean> exists(String email);
    Future<JsonObject> batchCreate(List<User> users);
    Future<JsonObject> batchUpdate(List<User> users);
    Future<JsonObject> batchDelete(List<Long> ids);
}
