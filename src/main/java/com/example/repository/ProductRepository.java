package com.example.repository;

import com.example.db.DatabaseVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;

import java.util.List;

/**
 * Product Repository - Database operations for products
 */
public class ProductRepository {

    private final Vertx vertx;

    public ProductRepository(Vertx vertx) {
        this.vertx = vertx;
    }

    // ================================================================
    // CRUD OPERATIONS
    // ================================================================

    /**
     * Find all products
     */
    public Future<List<JsonObject>> findAll() {
        String sql = "SELECT * FROM products ORDER BY id";
        return DatabaseVerticle.query(vertx, sql)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Find product by ID
     */
    public Future<JsonObject> findById(Long id) {
        String sql = "SELECT * FROM products WHERE id = $1";
        Tuple params = Tuple.tuple().addLong(id);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    /**
     * Search products by keyword and/or category
     */
    public Future<List<JsonObject>> search(String keyword, String category) {
        StringBuilder sql = new StringBuilder("SELECT * FROM products WHERE 1=1");
        Tuple params = Tuple.tuple();
        int paramIndex = 1;

        if (keyword != null && !keyword.isEmpty()) {
            sql.append(" AND LOWER(name) LIKE LOWER($").append(paramIndex++).append(")");
            params.addString("%" + keyword + "%");
        }
        if (category != null && !category.isEmpty()) {
            sql.append(" AND LOWER(category) = LOWER($").append(paramIndex++).append(")");
            params.addString(category);
        }
        sql.append(" ORDER BY id");

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Find products by category
     */
    public Future<List<JsonObject>> findByCategory(String category) {
        String sql = "SELECT * FROM products WHERE LOWER(category) = LOWER($1) ORDER BY id";
        Tuple params = Tuple.tuple().addString(category);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Find products by status
     */
    public Future<List<JsonObject>> findByStatus(String status) {
        String sql = "SELECT * FROM products WHERE status = $1 ORDER BY id";
        Tuple params = Tuple.tuple().addString(status);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Count all products
     */
    public Future<Long> count() {
        String sql = "SELECT COUNT(*) as count FROM products";
        return DatabaseVerticle.query(vertx, sql)
            .map(rows -> rows.iterator().next().getLong("count"));
    }

    /**
     * Create a new product
     */
    public Future<JsonObject> create(JsonObject product) {
        String sql = "INSERT INTO products (name, category, price, stock, description, status) " +
            "VALUES ($1, $2, $3, $4, $5, $6) RETURNING *";
        Tuple params = Tuple.tuple()
            .addString(product.getString("name"))
            .addString(product.getString("category"))
            .addBigDecimal(java.math.BigDecimal.valueOf(product.getDouble("price")))
            .addInteger(product.getInteger("stock", 0))
            .addString(product.getString("description"))
            .addString(product.getString("status", "active"));
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> DatabaseVerticle.toJson(rows.iterator().next()));
    }

    /**
     * Update product by ID
     */
    public Future<JsonObject> update(Long id, JsonObject product) {
        String sql = "UPDATE products SET " +
            "name = COALESCE($1, name), " +
            "category = COALESCE($2, category), " +
            "price = COALESCE($3, price), " +
            "stock = COALESCE($4, stock), " +
            "description = COALESCE($5, description), " +
            "status = COALESCE($6, status), " +
            "updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = $7 RETURNING *";
        Tuple params = Tuple.tuple()
            .addString(product.getString("name"))
            .addString(product.getString("category"))
            .addBigDecimal(java.math.BigDecimal.valueOf(product.getDouble("price")))
            .addInteger(product.getInteger("stock"))
            .addString(product.getString("description"))
            .addString(product.getString("status"))
            .addLong(id);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> {
                List<JsonObject> list = DatabaseVerticle.toJsonList(rows);
                return list.isEmpty() ? null : list.get(0);
            });
    }

    /**
     * Delete product by ID
     */
    public Future<Boolean> delete(Long id) {
        String sql = "DELETE FROM products WHERE id = $1 RETURNING id";
        Tuple params = Tuple.tuple().addLong(id);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.rowCount() > 0);
    }

    /**
     * Check if product name exists
     */
    public Future<Boolean> existsByName(String name) {
        String sql = "SELECT EXISTS(SELECT 1 FROM products WHERE LOWER(name) = LOWER($1)) as exists";
        Tuple params = Tuple.tuple().addString(name);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(rows -> rows.iterator().next().getBoolean("exists"));
    }

    // ================================================================
    // PAGINATION
    // ================================================================

    /**
     * Find products with pagination
     */
    public Future<List<JsonObject>> findPaginated(int page, int size) {
        int offset = (page - 1) * size;
        String sql = "SELECT * FROM products ORDER BY id LIMIT $1 OFFSET $2";
        Tuple params = Tuple.tuple().addInteger(size).addInteger(offset);
        return DatabaseVerticle.query(vertx, sql, params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Search with pagination
     */
    public Future<List<JsonObject>> searchPaginated(String keyword, String category, int page, int size) {
        int offset = (page - 1) * size;
        StringBuilder sql = new StringBuilder("SELECT * FROM products WHERE 1=1");
        Tuple params = Tuple.tuple();
        int paramIndex = 1;

        if (keyword != null && !keyword.isEmpty()) {
            sql.append(" AND LOWER(name) LIKE LOWER($").append(paramIndex++).append(")");
            params.addString("%" + keyword + "%");
        }
        if (category != null && !category.isEmpty()) {
            sql.append(" AND LOWER(category) = LOWER($").append(paramIndex++).append(")");
            params.addString(category);
        }
        sql.append(" ORDER BY id LIMIT $").append(paramIndex++).append(" OFFSET $").append(paramIndex);
        params.addInteger(size).addInteger(offset);

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(DatabaseVerticle::toJsonList);
    }

    /**
     * Count search results (for pagination total)
     */
    public Future<Long> searchCount(String keyword, String category) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) as count FROM products WHERE 1=1");
        Tuple params = Tuple.tuple();
        int paramIndex = 1;

        if (keyword != null && !keyword.isEmpty()) {
            sql.append(" AND LOWER(name) LIKE LOWER($").append(paramIndex++).append(")");
            params.addString("%" + keyword + "%");
        }
        if (category != null && !category.isEmpty()) {
            sql.append(" AND LOWER(category) = LOWER($").append(paramIndex++).append(")");
            params.addString(category);
        }

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(rows -> rows.iterator().next().getLong("count"));
    }
}
