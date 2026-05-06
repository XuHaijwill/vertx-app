package com.example.repository;

import com.example.db.DatabaseVerticle;
import com.example.db.TransactionContext;
import com.example.db.TxContextHolder;
import com.example.entity.User;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * User Repository - Database operations for users.
 *
 * <p>Three categories of methods:
 *   1. <b>Pool-based</b> (standalone) — no transaction
 *   2. <b>Auto-route</b> (declarative-tx) — auto-detects {@link TxContextHolder#current()}
 *   3. <b>Context-based</b> (explicit tx) — receive {@code TransactionContext} parameter
 */
public class UserRepository {

    private final Vertx vertx;

    public UserRepository(Vertx vertx) {
        this.vertx = vertx;
    }

    // ================================================================
    // Row → User mapping helper
    // ================================================================

    private List<User> toUserList(RowSet<Row> rows) {
        return StreamSupport.stream(rows.spliterator(), false)
            .map(User::toUser)
            .collect(Collectors.toList());
    }

    private User toUserOne(RowSet<Row> rows) {
        return rows.iterator().hasNext() ? User.toUser(rows.iterator().next()) : null;
    }

    // ================================================================
    // Pool-based queries (standalone, no transaction)
    // ================================================================

    public Future<List<User>> findAll() {
        String sql = "SELECT * FROM users ORDER BY id";
        return DatabaseVerticle.query(vertx, sql)
            .map(this::toUserList);
    }

    public Future<User> findById(Long id) {
        String sql = "SELECT * FROM users WHERE id = $1";
        Tuple params = Tuple.tuple().addLong(id);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(this::toUserOne);
    }

    public Future<User> findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = $1";
        Tuple params = Tuple.tuple().addString(email);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(this::toUserOne);
    }

    public Future<List<User>> search(String keyword) {
        String sql = "SELECT * FROM users WHERE " +
            "LOWER(name) LIKE LOWER($1) OR " +
            "LOWER(email) LIKE LOWER($1) OR " +
            "LOWER(department) LIKE LOWER($1) " +
            "ORDER BY id";
        String pattern = "%" + keyword + "%";
        Tuple params = Tuple.tuple().addString(pattern);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(this::toUserList);
    }

    public Future<List<User>> findByDepartment(String department) {
        String sql = "SELECT * FROM users WHERE LOWER(department) = LOWER($1) ORDER BY id";
        Tuple params = Tuple.tuple().addString(department);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(this::toUserList);
    }

    public Future<List<User>> findByStatus(String status) {
        String sql = "SELECT * FROM users WHERE status = $1 ORDER BY id";
        Tuple params = Tuple.tuple().addString(status);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(this::toUserList);
    }

    public Future<Long> count() {
        String sql = "SELECT COUNT(*) as count FROM users";
        return DatabaseVerticle.query(vertx, sql)
            .map(rows -> rows.iterator().next().getLong("count"));
    }

    public Future<User> create(User user) {
        String sql = "INSERT INTO users (name, email, age, department, status) " +
            "VALUES ($1, $2, $3, $4, $5) RETURNING *";
        Tuple params = Tuple.tuple()
            .addString(user.getName())
            .addString(user.getEmail())
            .addInteger(user.getAge())
            .addString(user.getDepartment())
            .addString(user.getStatus() != null ? user.getStatus() : "active");
        return DatabaseVerticle.query(vertx, sql, params)
            .map(this::toUserOne);
    }

    public Future<User> update(Long id, User user) {
        String sql = "UPDATE users SET " +
            "name = COALESCE($1, name), " +
            "email = COALESCE($2, email), " +
            "age = COALESCE($3, age), " +
            "department = COALESCE($4, department), " +
            "status = COALESCE($5, status), " +
            "updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = $6 RETURNING *";
        Tuple params = Tuple.tuple()
            .addString(user.getName())
            .addString(user.getEmail())
            .addInteger(user.getAge())
            .addString(user.getDepartment())
            .addString(user.getStatus())
            .addLong(id);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(this::toUserOne);
    }

    public Future<Boolean> delete(Long id) {
        String sql = "DELETE FROM users WHERE id = $1 RETURNING id";
        Tuple params = Tuple.tuple().addLong(id);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.rowCount() > 0);
    }

    public Future<Integer> deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Future.succeededFuture(0);
        String placeholders = ids.stream().map(i -> "$" + (ids.indexOf(i) + 1)).collect(Collectors.joining(","));
        String sql = "DELETE FROM users WHERE id IN (" + placeholders + ")";
        Tuple params = Tuple.tuple();
        for (Long id : ids) params.addLong(id);
        return DatabaseVerticle.query(vertx, sql, params).map(rows -> rows.rowCount());
    }

    public Future<List<User>> createBatch(List<User> users) {
        if (users == null || users.isEmpty()) return Future.succeededFuture(List.of());
        List<String> columns = List.of("name", "email", "age", "department", "status");
        List<List<Object>> values = users.stream().map(u -> List.<Object>of(
            u.getName(),
            u.getEmail(),
            u.getAge(),
            u.getDepartment(),
            u.getStatus() != null ? u.getStatus() : "active"
        )).collect(Collectors.toList());
        return com.example.db.BatchOperations.multiRowInsert(vertx, "users", columns, values, "id")
            .compose(ids -> {
                if (ids.isEmpty()) return Future.succeededFuture(List.<User>of());
                String placeholders = ids.stream().map(i -> "$" + (ids.indexOf(i) + 1)).collect(Collectors.joining(","));
                Tuple params = Tuple.tuple();
                for (Long id : ids) params.addLong(id);
                return DatabaseVerticle.query(vertx, "SELECT * FROM users WHERE id IN (" + placeholders + ") ORDER BY id", params)
                    .map(this::toUserList);
            });
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

    public Future<List<User>> findPaginated(int page, int size) {
        int offset = (page - 1) * size;
        String sql = "SELECT * FROM users ORDER BY id LIMIT $1 OFFSET $2";
        Tuple params = Tuple.tuple().addInteger(size).addInteger(offset);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(this::toUserList);
    }

    public Future<List<User>> searchPaginated(String keyword, int page, int size) {
        int offset = (page - 1) * size;
        String sql = "SELECT * FROM users WHERE " +
            "LOWER(name) LIKE LOWER($1) OR " +
            "LOWER(email) LIKE LOWER($1) OR " +
            "LOWER(department) LIKE LOWER($1) " +
            "ORDER BY id LIMIT $2 OFFSET $3";
        String pattern = "%" + keyword + "%";
        Tuple params = Tuple.tuple().addString(pattern).addInteger(size).addInteger(offset);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(this::toUserList);
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
    // Auto-route variants (declarative-transaction — preferred)
    // ================================================================

    /**
     * Lock user row with FOR UPDATE — auto-detects active transaction.
     */
    public Future<User> findByIdForUpdate(Long userId) {
        TransactionContext tx = TxContextHolder.current();
        if (tx != null) return findByIdForUpdateInTx(tx, userId);
        return DatabaseVerticle.withTransaction(vertx,
            txCtx -> findByIdForUpdateInTx(txCtx, userId), 5_000);
    }

    /**
     * Deduct user balance — auto-detects active transaction.
     */
    public Future<Void> deductBalance(Long userId, BigDecimal amount) {
        TransactionContext tx = TxContextHolder.current();
        if (tx != null) return deductBalanceInTx(tx, userId, amount);
        return DatabaseVerticle.withTransaction(vertx,
            txCtx -> deductBalanceInTx(txCtx, userId, amount), 5_000);
    }

    /**
     * Add balance back — auto-detects active transaction (e.g., refund).
     */
    public Future<Void> addBalance(Long userId, BigDecimal amount) {
        TransactionContext tx = TxContextHolder.current();
        if (tx != null) return addBalanceInTx(tx, userId, amount);
        return DatabaseVerticle.withTransaction(vertx,
            txCtx -> addBalanceInTx(txCtx, userId, amount), 5_000);
    }

    /**
     * Update user's order_count — auto-detects active transaction.
     */
    public Future<Void> updateUserOrderCount(Long userId, int delta) {
        TransactionContext tx = TxContextHolder.current();
        if (tx != null) return updateUserOrderCountInTx(tx, userId, delta);
        return DatabaseVerticle.withTransaction(vertx,
            txCtx -> updateUserOrderCountInTx(txCtx, userId, delta), 5_000);
    }

    // ================================================================
    // Context-based (explicit tx) — kept for full control
    // ================================================================

    /**
     * Lock user row with FOR UPDATE — explicit transaction.
     */
    public Future<User> findByIdForUpdate(TransactionContext tx, Long id) {
        return findByIdForUpdateInTx(tx, id);
    }

    /**
     * Deduct user balance — explicit transaction.
     */
    public Future<Void> deductBalance(TransactionContext tx, Long userId, BigDecimal amount) {
        return deductBalanceInTx(tx, userId, amount);
    }

    /**
     * Add balance back — explicit transaction (e.g., refund).
     */
    public Future<Void> addBalance(TransactionContext tx, Long userId, BigDecimal amount) {
        return addBalanceInTx(tx, userId, amount);
    }

    /**
     * Update user's order_count — explicit transaction.
     */
    public Future<Void> updateUserOrderCount(TransactionContext tx, Long userId, int delta) {
        return updateUserOrderCountInTx(tx, userId, delta);
    }

    // ================================================================
    // Private internal implementations
    // ================================================================

    private Future<User> findByIdForUpdateInTx(TransactionContext tx, Long id) {
        tx.tick();
        String sql = "SELECT * FROM users WHERE id = $1 FOR UPDATE";
        Tuple params = Tuple.tuple().addLong(id);
        return DatabaseVerticle.queryOneInTx(tx.conn(), sql, params)
            .map(json -> json != null ? User.fromJson(json) : null);
    }

    private Future<Void> deductBalanceInTx(TransactionContext tx, Long userId, BigDecimal amount) {
        tx.tick();
        String sql = "UPDATE users SET balance = balance - $1, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = $2 AND balance >= $1";
        Tuple params = Tuple.tuple().addBigDecimal(amount).addLong(userId);
        return DatabaseVerticle.updateInTx(tx.conn(), sql, params)
            .compose(rows -> rows == 0
                ? Future.failedFuture(new RuntimeException("Insufficient balance for user " + userId))
                : Future.succeededFuture());
    }

    private Future<Void> addBalanceInTx(TransactionContext tx, Long userId, BigDecimal amount) {
        tx.tick();
        String sql = "UPDATE users SET balance = balance + $1, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = $2";
        Tuple params = Tuple.tuple().addBigDecimal(amount).addLong(userId);
        return DatabaseVerticle.updateInTx(tx.conn(), sql, params).mapEmpty();
    }

    private Future<Void> updateUserOrderCountInTx(TransactionContext tx, Long userId, int delta) {
        tx.tick();
        String sql = "UPDATE users SET order_count = COALESCE(order_count, 0) + $1, " +
            "updated_at = CURRENT_TIMESTAMP WHERE id = $2";
        Tuple params = Tuple.tuple().addInteger(delta).addLong(userId);
        return DatabaseVerticle.updateInTx(tx.conn(), sql, params).mapEmpty();
    }
}
