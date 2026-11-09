-- Migration V39: Bổ sung các cột ngày hết hạn cho bảng inventory_batches và cột chặn bán cho product_variants (Idempotent)

-- 1. Bổ sung cột expires_at cho inventory_batches nếu chưa có
SET @has_expires_at = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inventory_batches'
      AND COLUMN_NAME = 'expires_at'
);
SET @add_expires_at_sql = IF(@has_expires_at = 0,
    'ALTER TABLE inventory_batches ADD COLUMN expires_at DATE NOT NULL DEFAULT (CURRENT_DATE + INTERVAL 30 DAY)',
    'SELECT 1');
PREPARE stmt1 FROM @add_expires_at_sql;
EXECUTE stmt1;
DEALLOCATE PREPARE stmt1;

-- 2. Bổ sung cột expiry_notified_at cho inventory_batches nếu chưa có
SET @has_notified = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inventory_batches'
      AND COLUMN_NAME = 'expiry_notified_at'
);
SET @add_notified_sql = IF(@has_notified = 0,
    'ALTER TABLE inventory_batches ADD COLUMN expiry_notified_at DATETIME(3) NULL',
    'SELECT 1');
PREPARE stmt2 FROM @add_notified_sql;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- 3. Bổ sung cột operations_blocked cho product_variants nếu chưa có
SET @has_op_blocked = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_variants'
      AND COLUMN_NAME = 'operations_blocked'
);
SET @add_op_blocked_sql = IF(@has_op_blocked = 0,
    'ALTER TABLE product_variants ADD COLUMN operations_blocked TINYINT(1) NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt3 FROM @add_op_blocked_sql;
EXECUTE stmt3;
DEALLOCATE PREPARE stmt3;

-- 4. Bổ sung cột operations_block_reason cho product_variants nếu chưa có
SET @has_op_reason = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_variants'
      AND COLUMN_NAME = 'operations_block_reason'
);
SET @add_op_reason_sql = IF(@has_op_reason = 0,
    'ALTER TABLE product_variants ADD COLUMN operations_block_reason VARCHAR(1000) NULL',
    'SELECT 1');
PREPARE stmt4 FROM @add_op_reason_sql;
EXECUTE stmt4;
DEALLOCATE PREPARE stmt4;

