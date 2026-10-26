ALTER TABLE vouchers
    ADD COLUMN voucher_type VARCHAR(20) NOT NULL DEFAULT 'DISCOUNT';

ALTER TABLE vouchers
    ADD CONSTRAINT chk_vouchers_voucher_type CHECK (voucher_type IN ('DISCOUNT', 'CASHBACK'));

ALTER TABLE orders
    ADD COLUMN cashback_voucher_id BIGINT NULL,
    ADD COLUMN cashback_voucher_code VARCHAR(50) NULL,
    ADD COLUMN cashback_points BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN cashback_credited_at DATETIME(3) NULL,
    ADD CONSTRAINT fk_orders_cashback_voucher FOREIGN KEY (cashback_voucher_id) REFERENCES vouchers(id),
    ADD CONSTRAINT chk_order_cashback_points CHECK (cashback_points >= 0);
