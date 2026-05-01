package com.example.db;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Batch Operations — high-performance bulk INSERT/UPDATE/DELETE.
 *
 * <p>Uses Vert.x SQL Client's {@code preparedQuery().executeBatch()} for
 * single-round-trip execution of multiple statements.
 *
 * <p>Batch operations are ideal for:
 *   - Bulk inserting order items (10+ items per order)
 *   - Bulk updating order statuses (admin batch approval)
 *   - Bulk stock adjustments (inventory sync)
 *
 * <p>Performance comparison (PostgreSQL):
 *   - Sequential INSERTs: 100 items ≈ 500ms (100 round trips)
 *   - Batch INSERT:       100 items ≈ 20ms  (1 round trip)
 *
 * <p>Usage:
 * <pre>
 * // Standalone batch (auto-commit)
 * BatchOperations.batchInsert(vertx, "INSERT INTO items (order_id, product_id, qty) VALUES ($1, $2, $3)",
 *     List.of(
 *         Tuple.of(1L, 100L, 2),
 *         Tuple.of(1L, 101L, 3),
 *         Tuple.of(1L, 102L, 1)
 *     ));
 *
 * // Inside transaction
 * BatchOperations.batchInsertInTx(tx.conn(), sql, tuples);
 * </pre>
 */
public class BatchOperations {

    private static final Logger LOG = LoggerFactory.getLogger(BatchOperations.class);

    // ================================================================
    // Pool-based batch operations (standalone — no transaction)
    // ================================================================

    /**
     * Execute a batch INSERT/UPDATE/DELETE using connection pool.
     * Each tuple in the list represents one row's parameters.
     *
     * @param vertx  Vertx instance
     * @param sql    Prepared statement SQL (e.g., INSERT INTO t (a,b,c) VALUES ($1,$2,$3))
     * @param tuples List of parameter tuples (one per row)
     * @return Future with total affected row count
     */
    public static Future<Integer> batchExecute(Vertx vertx, String sql, List<Tuple> tuples) {
        Pool pool = DatabaseVerticle.getPool(vertx);
        if (pool == null) {
            return Future.failedFuture("Database not available");
        }
        if (tuples == null || tuples.isEmpty()) {
            return Future.succeededFuture(0);
        }

        long start = System.currentTimeMillis();
        return pool.preparedQuery(sql).executeBatch(tuples)
            .map(rowSets -> {
                int total = 0;
                RowSet<Row> current = rowSets;
                while (current != null) {
                    total += current.rowCount();
                    current = current.next();
                }
                long ms = System.currentTimeMillis() - start;
                LOG.debug("[BATCH] Executed {} rows in {}ms: {}", total, ms, sql.substring(0, Math.min(50, sql.length())));
                return total;
            })
            .onFailure(err -> LOG.error("[BATCH] Failed: {}", err.getMessage()));
    }

    /**
     * Batch INSERT — convenience wrapper returning generated IDs.
     *
     * <p>Note: PostgreSQL batch INSERT does NOT return generated IDs per-row.
     * Use this when you don't need the IDs (e.g., order items).
     * If you need IDs, use individual INSERTs or INSERT ... RETURNING with a single multi-row VALUES.
     *
     * @return Future with affected row count (NOT the generated IDs)
     */
    public static Future<Integer> batchInsert(Vertx vertx, String sql, List<Tuple> tuples) {
        return batchExecute(vertx, sql, tuples);
    }

    /**
     * Batch UPDATE — convenience wrapper.
     *
     * @return Future with total affected row count
     */
    public static Future<Integer> batchUpdate(Vertx vertx, String sql, List<Tuple> tuples) {
        return batchExecute(vertx, sql, tuples);
    }

    // ================================================================
    // Transaction-scoped batch operations
    // ================================================================

    /**
     * Execute a batch INSERT/UPDATE/DELETE inside an active transaction.
     *
     * @param conn   SqlConnection from TransactionContext
     * @param sql    Prepared statement SQL
     * @param tuples List of parameter tuples
     * @return Future with total affected row count
     */
    public static Future<Integer> batchExecuteInTx(SqlConnection conn, String sql, List<Tuple> tuples) {
        if (tuples == null || tuples.isEmpty()) {
            return Future.succeededFuture(0);
        }

        long start = System.currentTimeMillis();
        return conn.preparedQuery(sql).executeBatch(tuples)
            .map(rowSets -> {
                int total = 0;
                RowSet<Row> current = rowSets;
                while (current != null) {
                    total += current.rowCount();
                    current = current.next();
                }
                long ms = System.currentTimeMillis() - start;
                LOG.debug("[BATCH-TX] Executed {} rows in {}ms", total, ms);
                return total;
            });
    }

    /**
     * Batch INSERT inside transaction — convenience wrapper.
     */
    public static Future<Integer> batchInsertInTx(SqlConnection conn, String sql, List<Tuple> tuples) {
        return batchExecuteInTx(conn, sql, tuples);
    }

    /**
     * Batch UPDATE inside transaction — convenience wrapper.
     */
    public static Future<Integer> batchUpdateInTx(SqlConnection conn, String sql, List<Tuple> tuples) {
        return batchExecuteInTx(conn, sql, tuples);
    }

    // ================================================================
    // Multi-row INSERT with RETURNING (returns generated IDs)
    // ================================================================

    /**
     * Insert multiple rows in a single statement with RETURNING clause.
     * Use this when you need the generated IDs back.
     *
     * <p>Generates SQL like:
     * <pre>
     * INSERT INTO table (col1, col2) VALUES ($1, $2), ($3, $4), ($5, $6) RETURNING id
     * </pre>
     *
     * <p>Limitation: max 32767 parameters per statement (PostgreSQL limit).
     * For very large batches, split into chunks.
     *
     * @param vertx        Vertx instance
     * @param table        Table name
     * @param columns      Column names (e.g., ["order_id", "product_id", "quantity"])
     * @param valuesPerRow Values for each row (e.g., [[1, 100, 2], [1, 101, 3]])
     * @param returning    Column to return (e.g., "id") or null for no RETURNING
     * @return Future with list of returned values (e.g., generated IDs), or row count if no RETURNING
     */
    public static Future<List<Long>> multiRowInsert(Vertx vertx, String table, List<String> columns,
                                                     List<List<Object>> valuesPerRow, String returning) {
        if (valuesPerRow == null || valuesPerRow.isEmpty()) {
            return Future.succeededFuture(List.of());
        }

        int numCols = columns.size();
        StringBuilder sql = new StringBuilder("INSERT INTO ")
            .append(table).append(" (")
            .append(String.join(", ", columns))
            .append(") VALUES ");

        List<Object> flatParams = new ArrayList<>();
        for (int i = 0; i < valuesPerRow.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("(");
            List<Object> rowValues = valuesPerRow.get(i);
            for (int j = 0; j < numCols; j++) {
                if (j > 0) sql.append(", ");
                sql.append("$").append(flatParams.size() + 1);
                flatParams.add(rowValues.get(j));
            }
            sql.append(")");
        }

        if (returning != null && !returning.isEmpty()) {
            sql.append(" RETURNING ").append(returning);
        }

        Tuple params = Tuple.tuple();
        for (Object p : flatParams) {
            params.addValue(p);
        }

        return DatabaseVerticle.query(vertx, sql.toString(), params)
            .map(rows -> {
                if (returning == null || returning.isEmpty()) {
                    return List.of((long) rows.rowCount());
                }
                List<Long> ids = new ArrayList<>();
                for (Row row : rows) {
                    ids.add(row.getLong(returning));
                }
                return ids;
            });
    }

    /**
     * Multi-row INSERT inside transaction with RETURNING.
     *
     * @see #multiRowInsert(Vertx, String, List, List, String)
     */
    public static Future<List<Long>> multiRowInsertInTx(SqlConnection conn, String table, List<String> columns,
                                                         List<List<Object>> valuesPerRow, String returning) {
        if (valuesPerRow == null || valuesPerRow.isEmpty()) {
            return Future.succeededFuture(List.of());
        }

        int numCols = columns.size();
        StringBuilder sql = new StringBuilder("INSERT INTO ")
            .append(table).append(" (")
            .append(String.join(", ", columns))
            .append(") VALUES ");

        List<Object> flatParams = new ArrayList<>();
        for (int i = 0; i < valuesPerRow.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("(");
            List<Object> rowValues = valuesPerRow.get(i);
            for (int j = 0; j < numCols; j++) {
                if (j > 0) sql.append(", ");
                sql.append("$").append(flatParams.size() + 1);
                flatParams.add(rowValues.get(j));
            }
            sql.append(")");
        }

        if (returning != null && !returning.isEmpty()) {
            sql.append(" RETURNING ").append(returning);
        }

        Tuple params = Tuple.tuple();
        for (Object p : flatParams) {
            params.addValue(p);
        }

        return conn.preparedQuery(sql.toString()).execute(params)
            .map(rows -> {
                if (returning == null || returning.isEmpty()) {
                    return List.of((long) rows.rowCount());
                }
                List<Long> ids = new ArrayList<>();
                for (Row row : rows) {
                    ids.add(row.getLong(returning));
                }
                return ids;
            });
    }
}
