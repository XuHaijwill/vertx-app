package com.example.service;

import com.example.core.BusinessException;
import com.example.core.PageResult;
import com.example.repository.UserRepository;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * User Service Implementation - with PostgreSQL
 */
public class UserServiceImpl implements UserService {

    private static final Logger LOG = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final boolean dbAvailable;

    public UserServiceImpl(Vertx vertx) {
        this.userRepository = new UserRepository(vertx);
        this.dbAvailable = checkDbAvailability(vertx);
        
        if (!dbAvailable) {
            LOG.warn("Database not available - using demo mode");
        }
    }

    private boolean checkDbAvailability(Vertx vertx) {
        try {
            return com.example.db.DatabaseVerticle.getPool(vertx) != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Future<List<JsonObject>> findAll() {
        if (!dbAvailable) return Future.succeededFuture(getDemoUsers());
        return userRepository.findAll();
    }

    @Override
    public Future<JsonObject> findById(Long id) {
        if (!dbAvailable) {
            return Future.succeededFuture(getDemoUsers().stream()
                .filter(u -> u.getLong("id").equals(id))
                .findFirst().orElse(null));
        }
        return userRepository.findById(id)
            .map(user -> {
                if (user == null) {
                    throw BusinessException.notFound("User");
                }
                return user;
            });
    }

    @Override
    public Future<List<JsonObject>> search(String keyword) {
        if (!dbAvailable) {
            String lower = keyword.toLowerCase();
            return Future.succeededFuture(getDemoUsers().stream()
                .filter(u -> u.getString("name", "").toLowerCase().contains(lower) ||
                            u.getString("email", "").toLowerCase().contains(lower))
                .toList());
        }
        return userRepository.search(keyword);
    }

    @Override
    public Future<JsonObject> create(JsonObject user) {
        if (user == null) {
            return Future.failedFuture(BusinessException.badRequest("Request body is required"));
        }
        String email = user.getString("email");
        if (email == null || email.trim().isEmpty()) {
            return Future.failedFuture(BusinessException.badRequest("Email is required"));
        }

        if (!dbAvailable) {
            JsonObject newUser = user.copy();
            newUser.put("id", System.currentTimeMillis());
            return Future.succeededFuture(newUser);
        }

        return userRepository.existsByEmail(email)
            .compose(exists -> {
                if (exists) {
                    return Future.<JsonObject>failedFuture(
                        BusinessException.conflict("Email already exists"));
                }
                return userRepository.create(user);
            });
    }

    @Override
    public Future<JsonObject> update(Long id, JsonObject user) {
        if (!dbAvailable) {
            return Future.succeededFuture(user.copy().put("id", id));
        }
        return userRepository.findById(id)
            .compose(existing -> {
                if (existing == null) {
                    return Future.<JsonObject>failedFuture(BusinessException.notFound("User"));
                }
                String newEmail = user.getString("email");
                if (newEmail != null && !newEmail.equals(existing.getString("email"))) {
                    return userRepository.existsByEmail(newEmail)
                        .compose(emailExists -> {
                            if (emailExists) {
                                return Future.<JsonObject>failedFuture(
                                    BusinessException.conflict("Email already exists"));
                            }
                            return userRepository.update(id, user);
                        });
                }
                return userRepository.update(id, user);
            })
            .map(updated -> {
                if (updated == null) {
                    throw BusinessException.notFound("User");
                }
                return updated;
            });
    }

    @Override
    public Future<Void> delete(Long id) {
        if (!dbAvailable) {
            return Future.succeededFuture();
        }
        return userRepository.findById(id)
            .compose(existing -> {
                if (existing == null) {
                    return Future.<Void>failedFuture(BusinessException.notFound("User"));
                }
                return userRepository.delete(id).mapEmpty();
            });
    }

    // ================================================================
    // BATCH OPERATIONS
    // ================================================================

    private static final int MAX_BATCH_SIZE = 100;

    @Override
    public Future<JsonObject> batchCreate(List<JsonObject> users) {
        if (users == null || users.isEmpty()) {
            return Future.failedFuture(BusinessException.badRequest("Request body must be a non-empty array"));
        }
        if (users.size() > MAX_BATCH_SIZE) {
            return Future.failedFuture(BusinessException.badRequest("Batch size exceeds maximum of " + MAX_BATCH_SIZE));
        }
        // Validate each user
        for (int i = 0; i < users.size(); i++) {
            JsonObject u = users.get(i);
            if (u.getString("name") == null || u.getString("name").trim().isEmpty()) {
                return Future.failedFuture(BusinessException.badRequest("Item[" + i + "]: name is required"));
            }
            if (u.getString("email") == null || u.getString("email").trim().isEmpty()) {
                return Future.failedFuture(BusinessException.badRequest("Item[" + i + "]: email is required"));
            }
        }
        if (!dbAvailable) {
            List<JsonObject> created = users.stream().map(u -> {
                JsonObject copy = u.copy();
                copy.put("id", System.currentTimeMillis() + users.indexOf(u));
                return copy;
            }).toList();
            return Future.succeededFuture(new JsonObject()
                .put("created", created.size()).put("failed", 0)
                .put("items", created));
        }
        return userRepository.createBatch(users)
            .map(created -> new JsonObject()
                .put("created", created.size()).put("failed", 0)
                .put("items", created));
    }

    @Override
    public Future<JsonObject> batchUpdate(List<JsonObject> users) {
        if (users == null || users.isEmpty()) {
            return Future.failedFuture(BusinessException.badRequest("Request body must be a non-empty array"));
        }
        if (users.size() > MAX_BATCH_SIZE) {
            return Future.failedFuture(BusinessException.badRequest("Batch size exceeds maximum of " + MAX_BATCH_SIZE));
        }
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getLong("id") == null) {
                return Future.failedFuture(BusinessException.badRequest("Item[" + i + "]: id is required"));
            }
        }
        if (!dbAvailable) {
            return Future.succeededFuture(new JsonObject()
                .put("updated", users.size()).put("failed", 0)
                .put("items", users));
        }
        List<JsonObject> updated = new java.util.ArrayList<>();
        int[] failed = {0};
        List<JsonObject> failedItems = new java.util.ArrayList<>();
        Future<JsonObject> result = Future.succeededFuture();
        for (JsonObject u : users) {
            result = result.compose(v -> userRepository.update(u.getLong("id"), u)
                .onSuccess(row -> updated.add(row))
                .onFailure(err -> { failed[0]++; failedItems.add(u); })
                .mapEmpty());
        }
        return result.map(v -> new JsonObject()
            .put("updated", updated.size()).put("failed", failed[0])
            .put("items", updated)
            .put("failedItems", failedItems));
    }

    @Override
    public Future<JsonObject> batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Future.failedFuture(BusinessException.badRequest("ids must be a non-empty array"));
        }
        if (ids.size() > MAX_BATCH_SIZE) {
            return Future.failedFuture(BusinessException.badRequest("Batch size exceeds maximum of " + MAX_BATCH_SIZE));
        }
        if (!dbAvailable) {
            return Future.succeededFuture(new JsonObject()
                .put("deleted", ids.size()).put("failed", 0));
        }
        return userRepository.deleteByIds(ids)
            .map(deleted -> new JsonObject()
                .put("deleted", deleted).put("failed", ids.size() - deleted));
    }

    @Override
    public Future<Boolean> exists(String email) {
        if (!dbAvailable) {
            return Future.succeededFuture(false);
        }
        return userRepository.existsByEmail(email);
    }

    // ================================================================
    // PAGINATION
    // ================================================================

    @Override
    public Future<PageResult<JsonObject>> findPaginated(int page, int size) {
        if (!dbAvailable) {
            List<JsonObject> demo = getDemoUsers();
            int start = (page - 1) * size;
            int end = Math.min(start + size, demo.size());
            List<JsonObject> pageData = start < demo.size() ? demo.subList(start, end) : List.of();
            return Future.succeededFuture(new PageResult<>(pageData, demo.size(), page, size));
        }
        
        return userRepository.count()
            .compose(total -> userRepository.findPaginated(page, size)
                .map(list -> new PageResult<>(list, total, page, size)));
    }

    @Override
    public Future<PageResult<JsonObject>> searchPaginated(String keyword, int page, int size) {
        if (!dbAvailable) {
            String lower = keyword.toLowerCase();
            List<JsonObject> filtered = getDemoUsers().stream()
                .filter(u -> u.getString("name", "").toLowerCase().contains(lower) ||
                            u.getString("email", "").toLowerCase().contains(lower) ||
                            u.getString("department", "").toLowerCase().contains(lower))
                .toList();
            int start = (page - 1) * size;
            int end = Math.min(start + size, filtered.size());
            List<JsonObject> pageData = start < filtered.size() ? filtered.subList(start, end) : List.of();
            return Future.succeededFuture(new PageResult<>(pageData, filtered.size(), page, size));
        }

        return userRepository.searchCount(keyword)
            .compose(total -> userRepository.searchPaginated(keyword, page, size)
                .map(list -> new PageResult<>(list, total, page, size)));
    }

    // ================================================================
    // ADDITIONAL METHODS
    // ================================================================

    public Future<List<JsonObject>> findByDepartment(String department) {
        if (!dbAvailable) {
            return Future.succeededFuture(getDemoUsers().stream()
                .filter(u -> department.equalsIgnoreCase(u.getString("department", "")))
                .toList());
        }
        return userRepository.findByDepartment(department);
    }

    public Future<List<JsonObject>> findByStatus(String status) {
        if (!dbAvailable) {
            return Future.succeededFuture(getDemoUsers().stream()
                .filter(u -> status.equalsIgnoreCase(u.getString("status", "")))
                .toList());
        }
        return userRepository.findByStatus(status);
    }

    // ================================================================
    // DEMO DATA (when DB not available)
    // ================================================================

    private List<JsonObject> getDemoUsers() {
        return List.of(
            new JsonObject()
                .put("id", 1L).put("name", "Alice").put("email", "alice@example.com")
                .put("age", 28).put("department", "Engineering").put("status", "active")
                .put("created_at", "2026-04-26T00:00:00Z").put("_demo", true),
            new JsonObject()
                .put("id", 2L).put("name", "Bob").put("email", "bob@example.com")
                .put("age", 32).put("department", "Product").put("status", "active")
                .put("created_at", "2026-04-26T00:00:00Z").put("_demo", true),
            new JsonObject()
                .put("id", 3L).put("name", "Charlie").put("email", "charlie@example.com")
                .put("age", 25).put("department", "Design").put("status", "inactive")
                .put("created_at", "2026-04-26T00:00:00Z").put("_demo", true)
        );
    }
}
