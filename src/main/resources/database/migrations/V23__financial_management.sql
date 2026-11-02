-- Financial settlement ledger for customer refunds and shop payouts.
CREATE TABLE IF NOT EXISTS customer_refunds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    refund_type VARCHAR(30) NOT NULL,
    reason VARCHAR(1000) NULL,
    refund_amount DECIMAL(19,0) NOT NULL DEFAULT 0,
    bank_name VARCHAR(100) NULL,
    bank_account_no VARCHAR(50) NULL,
    bank_account_holder VARCHAR(150) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    exported_at DATETIME(3) NULL,
    CONSTRAINT fk_customer_refunds_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_customer_refunds_customer FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT uq_customer_refunds_order_type UNIQUE (order_id, refund_type),
    CONSTRAINT chk_customer_refund_type CHECK (refund_type IN ('CANCEL_ORDER','RETURN_REFUND')),
    CONSTRAINT chk_customer_refund_status CHECK (status IN ('PENDING','EXPORTED'))
);
CREATE INDEX ix_customer_refunds_date_status ON customer_refunds(created_at, status);

CREATE TABLE IF NOT EXISTS shop_payouts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    period_from DATE NOT NULL,
    period_to DATE NOT NULL,
    total_orders INT NOT NULL DEFAULT 0,
    gross_amount DECIMAL(19,0) NOT NULL DEFAULT 0,
    platform_fee DECIMAL(19,0) NOT NULL DEFAULT 0,
    platform_fee_rate DECIMAL(7,6) NOT NULL DEFAULT 0.050000,
    net_payout DECIMAL(19,0) NOT NULL DEFAULT 0,
    bank_name VARCHAR(100) NULL,
    bank_account_no VARCHAR(50) NULL,
    bank_account_holder VARCHAR(150) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    exported_at DATETIME(3) NULL,
    CONSTRAINT fk_shop_payouts_shop FOREIGN KEY (shop_id) REFERENCES shops(id),
    CONSTRAINT uq_shop_payout_period UNIQUE (shop_id, period_from, period_to),
    CONSTRAINT chk_shop_payout_status CHECK (status IN ('PENDING','EXPORTED','PAID'))
);

ALTER TABLE orders ADD COLUMN is_settled TINYINT(1) NOT NULL DEFAULT 0;
