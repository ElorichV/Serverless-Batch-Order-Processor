-- PostgreSQL DDL optimized with sequential BIGSERIAL primary key, TIMESTAMPTZ, and B-Tree covering indexes
CREATE TABLE IF NOT EXISTS processed_orders (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL UNIQUE,
    customer_id VARCHAR(64) NOT NULL,
    ticker VARCHAR(16) NOT NULL,
    side VARCHAR(8) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(18, 4) NOT NULL,
    currency VARCHAR(8) NOT NULL,
    gross_amount NUMERIC(18, 4) NOT NULL,
    commission_fee NUMERIC(18, 4) NOT NULL,
    tax_amount NUMERIC(18, 4) NOT NULL,
    net_settlement_amount NUMERIC(18, 4) NOT NULL,
    status VARCHAR(24) NOT NULL,
    source_s3_key VARCHAR(255) NOT NULL,
    job_execution_id BIGINT,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_processed_orders_customer_date
    ON processed_orders (customer_id, processed_at DESC);

CREATE INDEX IF NOT EXISTS idx_processed_orders_ticker
    ON processed_orders (ticker);
