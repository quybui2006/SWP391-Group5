SET @has_platform_fee_rate = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'shop_payouts'
      AND COLUMN_NAME = 'platform_fee_rate'
);
SET @add_platform_fee_rate_sql = IF(@has_platform_fee_rate > 0, 'SELECT 1',
    'ALTER TABLE shop_payouts ADD COLUMN platform_fee_rate DECIMAL(7,6) NOT NULL DEFAULT 0.050000 AFTER platform_fee');
PREPARE add_platform_fee_rate_stmt FROM @add_platform_fee_rate_sql;
EXECUTE add_platform_fee_rate_stmt;
DEALLOCATE PREPARE add_platform_fee_rate_stmt;
