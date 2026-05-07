-- Add balance and order_count columns to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS balance      DECIMAL(12,2) DEFAULT 0.00;
ALTER TABLE users ADD COLUMN IF NOT EXISTS order_count  INTEGER       DEFAULT 0;

COMMENT ON COLUMN users.balance      IS '用户余额';
COMMENT ON COLUMN users.order_count  IS '订单数量';
