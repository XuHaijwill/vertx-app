package com.example.service.impl;

import com.example.core.BusinessException;
import com.example.core.PageResult;
import com.example.db.AuditAction;
import com.example.db.AuditLogger;
import com.example.entity.User;
import com.example.repository.UserRepository;
import com.example.service.UserService;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * User Service Implementation - entity-based, type-safe.
 */
public class UserServiceImpl implements UserService {

    private static final Logger LOG = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final AuditLogger audit;
    private final boolean dbAvailable;

    public UserServiceImpl(Vertx vertx) {
        this.userRepository = new UserRepository(vertx);
        this.audit = new AuditLogger(vertx);
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

    // ── Entity → API JSON ───────────────────────────────────────────────────

    private List<JsonObject> usersToJson(List<User> users) {
        return users.stream().map(User::toJson).toList();
    }

    private JsonObject userToJson(User user) {
        return user != null ? user.toJson() : null;
    }

    // ── CRUD ────────────────────────────────────────────────────────────────

    @Override
    public Future<List<User>> findAll() {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return userRepository.findAll();
    }

    @Override
    public Future<User> findById(Long id) {
        if (!dbAvailable) return Future.succeededFuture(null);
        return userRepository.findById(id)
            .map(user -> {
                if (user == null) throw BusinessException.notFound("User");
                return user;
            });
    }

    @Override
    public Future<List<User>> search(String keyword) {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return userRepository.search(keyword);
    }

    @Override
    public Future<User> create(User user) {
        if (user == null) {
            return Future.failedFuture(BusinessException.badRequest("Request body is required"));
        }
        String email = user.getEmail();
        if (email == null || email.trim().isEmpty()) {
            return Future.failedFuture(BusinessException.badRequest("Email is required"));
        }
        if (!dbAvailable) {
            user.setId(System.currentTimeMillis());
            return Future.succeededFuture(user);
        }
        return userRepository.existsByEmail(email)
            .compose(exists -> {
                if (exists) {
                    return Future.<User>failedFuture(
                        BusinessException.conflict("Email already exists"));
                }
                return userRepository.create(user);
            })
            .compose(created ->
                audit.log(AuditAction.AUDIT_CREATE, "users",
                    String.valueOf(created.getId()), null, created.toJson())
                .map(created));
    }

    @Override
    public Future<User> update(Long id, User user) {
        if (!dbAvailable) {
            user.setId(id);
            return Future.succeededFuture(user);
        }
        return userRepository.findById(id)
            .compose(existing -> {
                if (existing == null) {
                    return Future.<User>failedFuture(BusinessException.notFound("User"));
                }
                String newEmail = user.getEmail();
                if (newEmail != null && !newEmail.equals(existing.getEmail())) {
                    return userRepository.existsByEmail(newEmail)
                        .compose(emailExists -> {
                            if (emailExists) {
                                return Future.<User>failedFuture(
                                    BusinessException.conflict("Email already exists"));
                            }
                            return userRepository.update(id, user);
                        });
                }
                return userRepository.update(id, user);
            })
            .compose(updated -> {
                if (updated == null) throw BusinessException.notFound("User");
                return userRepository.findById(id)
                    .compose(before ->
                        audit.log(AuditAction.AUDIT_UPDATE, "users",
                            String.valueOf(id),
                            before != null ? before.toJson() : null,
                            updated.toJson())
                        .map(updated));
            });
    }

    @Override
    public Future<Void> delete(Long id) {
        if (!dbAvailable) return Future.succeededFuture();
        return userRepository.findById(id)
            .compose(existing -> {
                if (existing == null) {
                    return Future.<Void>failedFuture(BusinessException.notFound("User"));
                }
                return userRepository.delete(id)
                    .compose(v ->
                        audit.log(AuditAction.AUDIT_DELETE, "users",
                            String.valueOf(id), existing.toJson(), null)
                        .mapEmpty());
            });
    }

    @Override
    public Future<Boolean> exists(String email) {
        if (!dbAvailable) return Future.succeededFuture(false);
        return userRepository.existsByEmail(email);
    }

    // ── Batch ──────────────────────────────────────────────────────────────

    private static final int MAX_BATCH_SIZE = 100;

    @Override
    public Future<BatchResult<User>> batchCreate(List<User> users) {
        if (users == null || users.isEmpty()) {
            return Future.failedFuture(
                BusinessException.badRequest("Request body must be a non-empty array"));
        }
        if (users.size() > MAX_BATCH_SIZE) {
            return Future.failedFuture(
                BusinessException.badRequest("Batch size exceeds " + MAX_BATCH_SIZE));
        }
        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            if (u.getName() == null || u.getName().trim().isEmpty()) {
                return Future.failedFuture(
                    BusinessException.badRequest("Item[" + i + "]: name is required"));
            }
            if (u.getEmail() == null || u.getEmail().trim().isEmpty()) {
                return Future.failedFuture(
                    BusinessException.badRequest("Item[" + i + "]: email is required"));
            }
        }
        if (!dbAvailable) {
            users.forEach(u -> u.setId(System.currentTimeMillis()));
            return Future.succeededFuture(new BatchResult<>(users, users.size(), 0, 0, 0));
        }
        return userRepository.createBatch(users)
            .map(created -> new BatchResult<>(created, created.size(), 0, 0, 0));
    }

    @Override
    public Future<BatchResult<User>> batchUpdate(List<User> users) {
        if (users == null || users.isEmpty()) {
            return Future.failedFuture(
                BusinessException.badRequest("Request body must be a non-empty array"));
        }
        if (users.size() > MAX_BATCH_SIZE) {
            return Future.failedFuture(
                BusinessException.badRequest("Batch size exceeds " + MAX_BATCH_SIZE));
        }
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getId() == null) {
                return Future.failedFuture(
                    BusinessException.badRequest("Item[" + i + "]: id is required"));
            }
        }
        if (!dbAvailable) {
            return Future.succeededFuture(new BatchResult<>(users, 0, users.size(), 0, 0));
        }
        List<User> updated = new java.util.ArrayList<>();
        int[] failed = {0};
        Future<Void> chain = Future.succeededFuture();
        for (User u : users) {
            chain = chain
                .compose(v -> userRepository.update(u.getId(), u))
                .onSuccess(updated::add)
                .onFailure(err -> failed[0]++)
                .mapEmpty();
        }
        return chain.map(v -> new BatchResult<>(updated, 0, updated.size(), 0, failed[0]));
    }

    @Override
    public Future<Integer> batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Future.failedFuture(
                BusinessException.badRequest("ids must be a non-empty array"));
        }
        if (ids.size() > MAX_BATCH_SIZE) {
            return Future.failedFuture(
                BusinessException.badRequest("Batch size exceeds " + MAX_BATCH_SIZE));
        }
        if (!dbAvailable) return Future.succeededFuture(ids.size());
        return userRepository.deleteByIds(ids);
    }

    // ── Pagination ─────────────────────────────────────────────────────────

    @Override
    public Future<PageResult<User>> findPaginated(int page, int size) {
        if (!dbAvailable) return Future.succeededFuture(new PageResult<>(List.of(), 0, page, size));
        return userRepository.count()
            .compose(total -> userRepository.findPaginated(page, size)
                .map(list -> new PageResult<>(list, total, page, size)));
    }

    @Override
    public Future<PageResult<User>> searchPaginated(String keyword, int page, int size) {
        if (!dbAvailable) return Future.succeededFuture(new PageResult<>(List.of(), 0, page, size));
        return userRepository.searchCount(keyword)
            .compose(total -> userRepository.searchPaginated(keyword, page, size)
                .map(list -> new PageResult<>(list, total, page, size)));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    @Override
    public Future<List<User>> findByDepartment(String department) {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return userRepository.findByDepartment(department);
    }

    @Override
    public Future<List<User>> findByStatus(String status) {
        if (!dbAvailable) return Future.succeededFuture(List.of());
        return userRepository.findByStatus(status);
    }
}