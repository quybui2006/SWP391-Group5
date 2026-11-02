ALTER TABLE orders
    ADD COLUMN voucher_id BIGINT NULL,
    ADD COLUMN voucher_code VARCHAR(50) NULL,
    ADD COLUMN voucher_discount DECIMAL(19,0) NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_orders_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(id);

SET @total_check = (
    SELECT tc.CONSTRAINT_NAME
    FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc
      ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
     AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
      AND tc.TABLE_NAME = 'orders'
      AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND cc.CHECK_CLAUSE LIKE '%total_amount%subtotal%points_discount%shipping_fee%'
    LIMIT 1
);
SET @drop_total_check = IF(@total_check IS NULL, 'SELECT 1',
    CONCAT('ALTER TABLE orders DROP CHECK `', REPLACE(@total_check, '`', '``'), '`'));
PREPARE drop_total_check_stmt FROM @drop_total_check;
EXECUTE drop_total_check_stmt;
DEALLOCATE PREPARE drop_total_check_stmt;

SET @revenue_check = (
    SELECT tc.CONSTRAINT_NAME
    FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc
      ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
     AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
      AND tc.TABLE_NAME = 'orders'
      AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND cc.CHECK_CLAUSE LIKE '%shop_revenue_amount%subtotal%'
    LIMIT 1
);
SET @drop_revenue_check = IF(@revenue_check IS NULL, 'SELECT 1',
    CONCAT('ALTER TABLE orders DROP CHECK `', REPLACE(@revenue_check, '`', '``'), '`'));
PREPARE drop_revenue_check_stmt FROM @drop_revenue_check;
EXECUTE drop_revenue_check_stmt;
DEALLOCATE PREPARE drop_revenue_check_stmt;

ALTER TABLE orders
    ADD CONSTRAINT chk_order_total_with_voucher
        CHECK (total_amount = subtotal - points_discount - voucher_discount + shipping_fee),
    ADD CONSTRAINT chk_order_shop_revenue_with_voucher
        CHECK (shop_revenue_amount = total_amount - shipping_fee + points_subsidy_amount),
    ADD CONSTRAINT chk_order_voucher_discount
        CHECK (voucher_discount <= subtotal - points_discount);
