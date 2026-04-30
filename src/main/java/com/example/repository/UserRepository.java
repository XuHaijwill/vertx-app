package com.example.repository;

import com.example.db.DatabaseVerticle;
import com.example.db.TransactionContext;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;

import java.util.List;

/**
 * User Repository - Database operations for users.
 *
 * <p>Two categories of methods:
 *   1. Pool-based (standalone) — use DatabaseVerticle.query(), no transaction
 *   2. Context-based (transactional) — receive TransactionContext, used inside withTransaction()
 */
public class UserRepository {

    private final Vertx vertx;

    public UserRepository(Vertx vertx) {
        this.vertx = vertx;
    }

    // ================================================================
    // Pool-based queries (standalone, no transaction)
    // ================================================================

    public Future<List<JsonObject>> findAll() {
        String sql = "SELECT * FROM users ORDER BY id";
        return DatabaseVerticle.query(vertx, sql)
            .map(DatabaseVerticle::toJsonList);
    }

    public Future<JsonObject> findById(Long id) {
        String sql = "SELECT * FROM users WHERE id = $1";
        Tuple params = Tuple.tuple().addLong(id);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    public Future<JsonObject> findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = $1";
        Tuple params = Tuple.tuple().addString(email);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    public Future<List<JsonObject>> search(String keyword) {
        String sql = "SELECT * FROM users WHERE " +
            "LOWER(name) LIKE LOWER($1) OR " +
            "LOWER(email) LIKE LOWER($1) OR " +
            "LOWER(department) LIKE LOWER($1) " +
            "ORDER BY id";
        String pattern = "%" + keyword + "%";
        Tuple params = Tuple.tuple().addString(pattern);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    public Future<List<JsonObject>> findByDepartment(String department) {
        String sql = "SELECT * FROM users WHERE LOWER(department) = LOWER($1) ORDER BY id";
        Tuple params = Tuple.tuple().addString(department);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    public Future<List<JsonObject>> findByStatus(String status) {
        String sql = "SELECT * FROM users WHERE status = $1 ORDER BY id";
        Tuple params = Tuple.tuple().addString(status);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    public Future<Long> count() {
        String sql = "SELECT COUNT(*) as count FROM users";
        return DatabaseVerticle.query(vertx, sql)
            .map(rows -> rows.iterator().next().getLong("count"));
    }

    public Future<JsonObject> create(JsonObject user) {
        String sql = "INSERT INTO users (name, email, age, department, status) " +
            "VALUES ($1, $2, $3, $4, $5) RETURNING *";
        Tuple params = Tuple.tuple()
            .addString(user.getString("name"))
            .addString(user.getString("email"))
            .addInteger(user.getInteger("age"))
            .addString(user.getString("department"))
            .addString(user.getString("status", "active"));
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> DatabaseVerticle.toJson(rows.iterator().next()));
    }

    public Future<JsonObject> update(Long id, JsonObject user) {
        String sql = "UPDATE users SET " +
            "name = COALESCE($1, name), " +
            "email = COALESCE($2, email), " +
            "age = COALESCE($3, age), " +
            "department = COALESCE($4, department), " +
            "status = COALESCE($5, status), " +
            "updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = $6 RETURNING *";
        Tuple params = Tuple.tuple()
            .addString(user.getString("name"))
            .addString(user.getString("email"))
            .addInteger(user.getInteger("age"))
            .addString(user.getString("department"))
            .addString(user.getString("status"))
            .addLong(id);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    public Future<Boolean> delete(Long id) {
        String sql = "DELETE FROM users WHERE id = $1 RETURNING id";
        Tuple params = Tuple.tuple().addLong(id);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.rowCount() > 0);
    }

    public Future<Boolean> existsByEmail(String email) {
        String sql = "SELECT EXISTS(SELECT 1 FROM users WHERE email = $1) as exists";
        Tuple params = Tuple.tuple().addString(email);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getBoolean("exists"));
    }

    // ================================================================
    // Pagination
    // ================================================================

    public Future<List<JsonObject>> findPaginated(int page, int size) {
        int offset = (page - 1) * size;
        String sql = "SELECT * FROM users ORDER BY id LIMIT $1 OFFSET $2";
        Tuple params = Tuple.tuple().addInteger(size).addInteger(offset);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    public Future<List<JsonObject>> searchPaginated(String keyword, int page, int size) {
        int offset = (page - 1) * size;
        String sql = "SELECT * FROM users WHERE " +
            "LOWER(name) LIKE LOWER($1) OR " +
            "LOWER(email) LIKE LOWER($1) OR " +
            "LOWER(department) LIKE LOWER($1) " +
            "ORDER BY id LIMIT $2 OFFSET $3";
        String pattern = "%" + keyword + "%";
        Tuple params = Tuple.tuple().addString(pattern).addInteger(size).addInteger(offset);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    public Future<Long> searchCount(String keyword) {
        String sql = "SELECT COUNT(*) as count FROM users WHERE " +
            "LOWER(name) LIKE LOWER($1) OR " +
            "LOWER(email) LIKE LOWER($1) OR " +
            "LOWER(department) LIKE LOWER($1)";
        String pattern = "%" + keyword + "%";
        Tuple params = Tuple.tuple().addString(pattern);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getLong("count"));
    }

    // ================================================================
    // Context-based (transactional) methods
    // These receive TransactionContext for multi-Repo transactions
    // ================================================================

    /**
     * Check if user exists and is active — inside a transaction.
     * Uses SELECT ... FOR UPDATE to prevent concurrent modifications.
     */
    public Future<JsonObject> findByIdForUpdate(TransactionContext tx, Long id) {
        tx.tick();
        String sql = "SELECT * FROM users WHERE id = $1 FOR UPDATE";
        Tuple params = Tuple.tuple().addLong(id);
        return DatabaseVerticle.queryOneInTx(tx.conn(), sql, params);
    }

    /**
     * Update user's order_count inside a transaction.
     * Call via: updateUserOrderCount(tx, userId, +1) or updateUserOrderCount(tx, userId, -1)
     */
    public Future<Void> updateUserOrderCount(TransactionContext tx, Long userId, int delta) {
        tx.tick();
        String sql = "UPDATE users SET order_count = COALESCE(order_count, 0) + $1, " +
            "updated_at = CURRENT_TIMESTAMP WHERE id = $2";
        Tuple params = Tuple.tuple().addInteger(delta).addLong(userId);
        return DatabaseVerticle.updateInTx(tx.conn(), sql, params).mapEmpty();
    }

    /**
     * Update user fields inside a transaction.
     */
    public Future<JsonObject> updateInTx(TransactionContext tx, Long id, JsonObject user) {
        tx.tick();
        String sql = "UPDATE users SET " +
            "name = COALESCE($1, name), " +
            "email = COALESCE($2, email), " +
            "age = COALESCE($3, age), " +
            "department = COALESCE($4, department), " +
            "status = COALESCE($5, status), " +
            "updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = $6 RETURNING *";
        Tuple params = Tuple.tuple()
            .addString(user.getString("name"))
            .addString(user.getString("email"))
            .addInteger(user.getInteger("age"))
            .addString(user.getString("department"))
            .addString(user.getString("status"))
            .addLong(id);
        return DatabaseVerticle.queryInTx(tx.conn(), sql, params)
            .map(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    /**
     * Deduct user balance inside a transaction.
     * Fails if balance is insufficient.
     */
    public Future<Void> deductBalance(TransactionContext tx, Long userId, java.math.BigDecimal amount) {
        tx.tick();
        String sql = "UPDATE users SET balance = balance - $1, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = $2 AND balance >= $1";
        Tuple params = Tuple.tuple().addBigDecimal(amount).addLong(userId);
        return DatabaseVerticle.updateInTx(tx.conn(), sql, params)
            .compose(rows -> rows == 0
                ? Future.failedFuture(new RuntimeException("Insufficient balance for user " + userId))
                : Future.succeededFuture());
    }

    /**
     * Add balance back inside a transaction (e.g., refund).
     */
    public Future<Void> addBalance(TransactionContext tx, Long userId, java.math.BigDecimal amount) {
        tx.tick();
        String sql = "UPDATE users SET balance = balance + $1, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = $2";
        Tuple params = Tuple.tuple().addBigDecimal(amount).addLong(userId);
        return DatabaseVerticle.updateInTx(tx.conn(), sql, params).mapEmpty();
    }
}
