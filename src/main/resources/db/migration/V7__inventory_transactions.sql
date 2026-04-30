-- ================================================================
-- V7__inventory_transactions.sql
-- Flyway Migration V7 — Create inventory_transactions table (stock ledger)
-- Records every stock change: order_create | order_cancel | manual_adjust
-- Enables full stock traceability and prevents TOCTOU race conditions
-- via SELECT ... FOR UPDATE on this table and products
-- ================================================================

CREATE TABLE IF NOT EXISTS inventory_transactions (
    id          BIGSERIAL   PRIMARY KEY,
    product_id  BIGINT      NOT NULL REFERENCES products(id),
    order_id    BIGINT,                                     -- nullable (manual adjustments)
    type        VARCHAR(30)  NOT NULL,                      -- order_create | order_cancel | manual_adjust
    delta       INTEGER      NOT NULL,                      -- positive = increase, negative = decrease
    stock_before INTEGER     NOT NULL,
    stock_after  INTEGER     NOT NULL,
    reason      TEXT,
    operator_id BIGINT,                                      -- user who made manual adjustment
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT inventory_delta_nonzero CHECK (delta != 0)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_invtx_product_id  ON inventory_transactions(product_id);
CREATE INDEX IF NOT EXISTS idx_invtx_order_id    ON inventory_transactions(order_id);
CREATE INDEX IF NOT EXISTS idx_invtx_created     ON inventory_transactions(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_invtx_type        ON inventory_transactions(type);
