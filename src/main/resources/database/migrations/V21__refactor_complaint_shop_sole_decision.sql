-- =============================================================================
-- Migration V20: Refactor Complaint Flow - Shop Sole Decision & Resend / Voucher
-- =============================================================================

-- 1. Bổ sung các cột mới vào bảng complaints
SET @has_desired_res = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'complaints'
      AND COLUMN_NAME = 'desired_resolution'
);
SET @alter_c_desired_res = IF(@has_desired_res > 0, 'SELECT 1',
    'ALTER TABLE complaints ADD COLUMN desired_resolution VARCHAR(30) NULL AFTER description');
PREPARE stmt_c_des FROM @alter_c_desired_res;
EXECUTE stmt_c_des;
DEALLOCATE PREPARE stmt_c_des;

SET @has_repl_order = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'complaints'
      AND COLUMN_NAME = 'replacement_order_id'
);
SET @alter_c_repl_order = IF(@has_repl_order > 0, 'SELECT 1',
    'ALTER TABLE complaints ADD COLUMN replacement_order_id BIGINT NULL AFTER final_refund_amount');
PREPARE stmt_c_repl FROM @alter_c_repl_order;
EXECUTE stmt_c_repl;
DEALLOCATE PREPARE stmt_c_repl;

SET @has_c_voucher_id = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'complaints'
      AND COLUMN_NAME = 'voucher_id'
);
SET @alter_c_v_id = IF(@has_c_voucher_id > 0, 'SELECT 1',
    'ALTER TABLE complaints ADD COLUMN voucher_id BIGINT NULL AFTER replacement_order_id');
PREPARE stmt_c_vid FROM @alter_c_v_id;
EXECUTE stmt_c_vid;
DEALLOCATE PREPARE stmt_c_vid;

SET @has_c_voucher_code = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'complaints'
      AND COLUMN_NAME = 'voucher_code'
);
SET @alter_c_v_code = IF(@has_c_voucher_code > 0, 'SELECT 1',
    'ALTER TABLE complaints ADD COLUMN voucher_code VARCHAR(50) NULL AFTER voucher_id');
PREPARE stmt_c_vcode FROM @alter_c_v_code;
EXECUTE stmt_c_vcode;
DEALLOCATE PREPARE stmt_c_vcode;

-- 2. Bổ sung các cột mới vào bảng orders
SET @has_orig_order = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'orders'
      AND COLUMN_NAME = 'original_order_id'
);
SET @alter_o_orig = IF(@has_orig_order > 0, 'SELECT 1',
    'ALTER TABLE orders ADD COLUMN original_order_id BIGINT NULL AFTER checkout_id');
PREPARE stmt_o_orig FROM @alter_o_orig;
EXECUTE stmt_o_orig;
DEALLOCATE PREPARE stmt_o_orig;

SET @has_order_type = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'orders'
      AND COLUMN_NAME = 'order_type'
);
SET @alter_o_type = IF(@has_order_type > 0, 'SELECT 1',
    'ALTER TABLE orders ADD COLUMN order_type VARCHAR(30) NOT NULL DEFAULT ''STANDARD'' AFTER original_order_id');
PREPARE stmt_o_type FROM @alter_o_type;
EXECUTE stmt_o_type;
DEALLOCATE PREPARE stmt_o_type;
