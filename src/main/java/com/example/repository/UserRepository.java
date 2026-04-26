package com.example.repository;

import com.example.db.DatabaseVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.util.List;

/**
 * User Repository - Database operations for users
 */
public class UserRepository {

    private final Vertx vertx;

    public UserRepository(Vertx vertx) {
        this.vertx = vertx;
    }

    // ================================================================
    // CRUD OPERATIONS
    // ================================================================

    /**
     * Find all users
     */
    public Future<List<JsonObject>> findAll() {
        String sql = "SELECT * FROM users ORDER BY id";
        return DatabaseVerticle.query(vertx, sql)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Find user by ID
     */
    public Future<JsonObject> findById(Long id) {
        String sql = "SELECT * FROM users WHERE id = $1";
        Tuple params = Tuple.tuple().addLong(id);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    /**
     * Find users by email
     */
    public Future<JsonObject> findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = $1";
        Tuple params = Tuple.tuple().addString(email);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    /**
     * Search users by keyword
     */
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

    /**
     * Find users by department
     */
    public Future<List<JsonObject>> findByDepartment(String department) {
        String sql = "SELECT * FROM users WHERE LOWER(department) = LOWER($1) ORDER BY id";
        Tuple params = Tuple.tuple().addString(department);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Find users by status
     */
    public Future<List<JsonObject>> findByStatus(String status) {
        String sql = "SELECT * FROM users WHERE status = $1 ORDER BY id";
        Tuple params = Tuple.tuple().addString(status);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Count all users
     */
    public Future<Long> count() {
        String sql = "SELECT COUNT(*) as count FROM users";
        return DatabaseVerticle.query(vertx, sql)
            .map(rows -> {
                Row row = rows.iterator().next();
                return row.getLong("count");
            });
    }

    /**
     * Create a new user
     */
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

    /**
     * Update user by ID
     */
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

    /**
     * Delete user by ID
     */
    public Future<Boolean> delete(Long id) {
        String sql = "DELETE FROM users WHERE id = $1 RETURNING id";
        Tuple params = Tuple.tuple().addLong(id);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.rowCount() > 0);
    }

    /**
     * Check if email exists
     */
    public Future<Boolean> existsByEmail(String email) {
        String sql = "SELECT EXISTS(SELECT 1 FROM users WHERE email = $1) as exists";
        Tuple params = Tuple.tuple().addString(email);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getBoolean("exists"));
    }

    // ================================================================
    // PAGINATION
    // ================================================================

    /**
     * Find users with pagination
     */
    public Future<List<JsonObject>> findPaginated(int page, int size) {
        int offset = (page - 1) * size;
        String sql = "SELECT * FROM users ORDER BY id LIMIT $1 OFFSET $2";
        Tuple params = Tuple.tuple().addInteger(size).addInteger(offset);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Search with pagination
     */
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

    /**
     * Count search results (for pagination total)
     */
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
}
