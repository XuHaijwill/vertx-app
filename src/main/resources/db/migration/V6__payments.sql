-- ================================================================
-- V6__payments.sql
-- Flyway Migration V6 — Create payments table
-- Supports atomic multi-table transactions (payment + order + user balance)
-- ================================================================

CREATE TABLE IF NOT EXISTS payments (
    id            BIGSERIAL    PRIMARY KEY,
    order_id      BIGINT       NOT NULL REFERENCES orders(id),
    user_id       BIGINT       NOT NULL REFERENCES users(id),
    amount        DECIMAL(12,2) NOT NULL,
    method        VARCHAR(20)   NOT NULL DEFAULT 'balance',  -- balance | card | alipay | wechat
    status        VARCHAR(20)   NOT NULL DEFAULT 'pending', -- pending | completed | failed | refunded
    transaction_no VARCHAR(64)  UNIQUE,
    remark        TEXT,
    completed_at  TIMESTAMP,
    created_at    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT payments_amount_positive CHECK (amount > 0),
    CONSTRAINT payments_status_valid    CHECK (status IN ('pending','completed','failed','refunded'))
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_payments_order_id   ON payments(order_id);
CREATE INDEX IF NOT EXISTS idx_payments_user_id    ON payments(user_id);
CREATE INDEX IF NOT EXISTS idx_payments_status    ON payments(status);
CREATE INDEX IF NOT EXISTS idx_payments_created    ON payments(created_at DESC);
