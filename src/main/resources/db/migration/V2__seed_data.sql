-- ================================================================
-- V2__seed_data.sql
-- Flyway Migration V2 — Seed sample data
-- ================================================================

-- Users (idempotent: skip if email already exists)
INSERT INTO users (name, email, age, department, status) VALUES
    ('Alice',   'alice@example.com',   28, 'Engineering', 'active')
ON CONFLICT (email) DO NOTHING;

INSERT INTO users (name, email, age, department, status) VALUES
    ('Bob',     'bob@example.com',     32, 'Product',     'active')
ON CONFLICT (email) DO NOTHING;

INSERT INTO users (name, email, age, department, status) VALUES
    ('Charlie', 'charlie@example.com', 25, 'Design',      'inactive')
ON CONFLICT (email) DO NOTHING;

-- Products (idempotent: skip if name already exists)
INSERT INTO products (name, category, price, stock, description, status) VALUES
    ('iPhone 15',   'Electronics', 799.99, 100, 'Apple smartphone',             'active')
ON CONFLICT DO NOTHING;

INSERT INTO products (name, category, price, stock, description, status) VALUES
    ('MacBook Pro', 'Electronics', 1999.99, 50,  'Apple laptop',                'active')
ON CONFLICT DO NOTHING;

INSERT INTO products (name, category, price, stock, description, status) VALUES
    ('Coffee Maker','Home',        49.99,  200, 'Automatic coffee maker',      'active')
ON CONFLICT DO NOTHING;
