package com.example.db;

import io.vertx.sqlclient.SqlConnection;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransactionContextTest {

    @Test
    void constructor_initialState() {
        SqlConnection conn = mock(SqlConnection.class);
        TransactionContext tx = new TransactionContext(conn, 30000);
        assertNotNull(tx.conn());
        assertSame(conn, tx.conn());
        assertEquals(0, tx.operationCount());
        assertFalse(tx.isRollbackOnly());
        assertEquals(30000, tx.timeoutMs());
    }

    @Test
    void tick_incrementsOperationCount() {
        SqlConnection conn = mock(SqlConnection.class);
        TransactionContext tx = new TransactionContext(conn, 30000);
        assertEquals(0, tx.operationCount());

        tx.tick();
        assertEquals(1, tx.operationCount());

        tx.tick();
        tx.tick();
        assertEquals(3, tx.operationCount());
    }

    @Test
    void tick_returnsSameInstance() {
        SqlConnection conn = mock(SqlConnection.class);
        TransactionContext tx = new TransactionContext(conn, 30000);
        assertSame(tx, tx.tick());
    }

    @Test
    void elapsedMs_increasesOverTime() throws InterruptedException {
        SqlConnection conn = mock(SqlConnection.class);
        TransactionContext tx = new TransactionContext(conn, 30000);
        long initial = tx.elapsedMs();
        assertTrue(initial >= 0);

        Thread.sleep(10);
        long later = tx.elapsedMs();
        assertTrue(later >= initial);
    }

    @Test
    void setRollbackOnly() {
        SqlConnection conn = mock(SqlConnection.class);
        TransactionContext tx = new TransactionContext(conn, 30000);
        assertFalse(tx.isRollbackOnly());
        tx.setRollbackOnly();
        assertTrue(tx.isRollbackOnly());
    }

    @Test
    void checkTimeout_withinBudget_noException() {
        SqlConnection conn = mock(SqlConnection.class);
        TransactionContext tx = new TransactionContext(conn, 30000);
        assertDoesNotThrow(tx::checkTimeout);
    }

    @Test
    void checkTimeout_exceedsBudget_throws() {
        SqlConnection conn = mock(SqlConnection.class);
        // Use very small timeout so it's immediately exceeded
        TransactionContext tx = new TransactionContext(conn, 1);
        // Sleep a bit to ensure 90% threshold is passed
        try {
            Thread.sleep(5);
        } catch (InterruptedException ignored) {}
        assertThrows(RuntimeException.class, tx::checkTimeout);
    }

    @Test
    void toString_containsKeyInfo() {
        SqlConnection conn = mock(SqlConnection.class);
        TransactionContext tx = new TransactionContext(conn, 30000);
        tx.tick();
        String str = tx.toString();
        assertTrue(str.contains("ops=1"));
        assertTrue(str.contains("timeout=30000"));
        assertTrue(str.contains("rollbackOnly=false"));
    }

    @Test
    void toString_rollbackOnly() {
        SqlConnection conn = mock(SqlConnection.class);
        TransactionContext tx = new TransactionContext(conn, 30000);
        tx.setRollbackOnly();
        String str = tx.toString();
        assertTrue(str.contains("rollbackOnly=true"));
    }
}
