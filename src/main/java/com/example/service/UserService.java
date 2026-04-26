package com.example.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.List;

/**
 * User Service Interface - 用户服务接口
 */
public interface UserService {

    /**
     * Get all users - 获取所有用户
     */
    Future<List<JsonObject>> findAll();

    /**
     * Get user by ID - 根据ID获取用户
     */
    Future<JsonObject> findById(Long id);

    /**
     * Search users by keyword - 关键词搜索用户
     */
    Future<List<JsonObject>> search(String keyword);

    /**
     * Create user - 创建用户
     */
    Future<JsonObject> create(JsonObject user);

    /**
     * Update user - 更新用户
     */
    Future<JsonObject> update(Long id, JsonObject user);

    /**
     * Delete user - 删除用户
     */
    Future<Void> delete(Long id);

    /**
     * Check if user exists - 检查用户是否存在
     */
    Future<Boolean> exists(String email);
}