-- =============================================================================
-- Migration V16: Refactor Complaint Flow (2-Tier: Shop -> CSKH) & Order Integration
-- =============================================================================

-- 1. Bổ sung customer_confirmed_at vào bảng orders
SET @has_order_cust_conf = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'orders'
      AND COLUMN_NAME = 'customer_confirmed_at'
);
SET @alter_orders_sql = IF(@has_order_cust_conf > 0, 'SELECT 1',
    'ALTER TABLE orders ADD COLUMN customer_confirmed_at DATETIME(3) NULL AFTER delivered_at');
PREPARE alter_orders_stmt FROM @alter_orders_sql;
EXECUTE alter_orders_stmt;
DEALLOCATE PREPARE alter_orders_stmt;

-- 2. Bổ sung các cột mới cho bảng complaints
SET @has_req_refund = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'complaints'
      AND COLUMN_NAME = 'requested_refund_amount'
);
SET @alter_complaints_col1 = IF(@has_req_refund > 0, 'SELECT 1',
    'ALTER TABLE complaints ADD COLUMN requested_refund_amount BIGINT NOT NULL DEFAULT 0 AFTER source_type');
PREPARE stmt_c1 FROM @alter_complaints_col1;
EXECUTE stmt_c1;
DEALLOCATE PREPARE stmt_c1;

SET @has_final_refund = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'complaints'
      AND COLUMN_NAME = 'final_refund_amount'
);
SET @alter_complaints_col2 = IF(@has_final_refund > 0, 'SELECT 1',
    'ALTER TABLE complaints ADD COLUMN final_refund_amount BIGINT NOT NULL DEFAULT 0 AFTER requested_refund_amount');
PREPARE stmt_c2 FROM @alter_complaints_col2;
EXECUTE stmt_c2;
DEALLOCATE PREPARE stmt_c2;

SET @has_shop_dec = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'complaints'
      AND COLUMN_NAME = 'shop_decision'
);
SET @alter_complaints_col3 = IF(@has_shop_dec > 0, 'SELECT 1',
    'ALTER TABLE complaints ADD COLUMN shop_decision VARCHAR(25) NULL AFTER final_refund_amount');
PREPARE stmt_c3 FROM @alter_complaints_col3;
EXECUTE stmt_c3;
DEALLOCATE PREPARE stmt_c3;

SET @has_shop_proof = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'complaints'
      AND COLUMN_NAME = 'shop_evidence_image_url'
);
SET @alter_complaints_col4 = IF(@has_shop_proof > 0, 'SELECT 1',
    'ALTER TABLE complaints ADD COLUMN shop_evidence_image_url VARCHAR(1000) NULL AFTER shop_response');
PREPARE stmt_c4 FROM @alter_complaints_col4;
EXECUTE stmt_c4;
DEALLOCATE PREPARE stmt_c4;

SET @has_esc_at = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'complaints'
      AND COLUMN_NAME = 'escalated_at'
);
SET @alter_complaints_col5 = IF(@has_esc_at > 0, 'SELECT 1',
    'ALTER TABLE complaints ADD COLUMN escalated_at DATETIME(3) NULL AFTER shop_responded_at');
PREPARE stmt_c5 FROM @alter_complaints_col5;
EXECUTE stmt_c5;
DEALLOCATE PREPARE stmt_c5;

-- 3. Cập nhật dữ liệu cũ sang các trạng thái 2 tầng mới
UPDATE complaints SET status = 'PENDING_SHOP' WHERE status = 'PENDING';
UPDATE complaints SET status = 'ESCALATED' WHERE status = 'IN_PROGRESS';
UPDATE complaints SET final_refund_amount = compensated_points WHERE status = 'RESOLVED' AND final_refund_amount = 0 AND compensated_points > 0;
