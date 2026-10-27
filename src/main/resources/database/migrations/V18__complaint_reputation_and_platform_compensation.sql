-- =============================================================================
-- Migration V18: Shop reputation scoring, logs, and platform compensation support
-- =============================================================================

-- 1. Add reputation_score column to shops if not exists
SET @has_reputation = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'shops'
      AND COLUMN_NAME = 'reputation_score'
);
SET @add_reputation_sql = IF(@has_reputation > 0, 'SELECT 1',
    'ALTER TABLE shops ADD COLUMN reputation_score INT NOT NULL DEFAULT 100');
PREPARE add_reputation_stmt FROM @add_reputation_sql;
EXECUTE add_reputation_stmt;
DEALLOCATE PREPARE add_reputation_stmt;

-- 2. Add fault_party and gifted_freeship_voucher columns to complaints
SET @has_fault_party = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'complaints'
      AND COLUMN_NAME = 'fault_party'
);
SET @add_fault_party_sql = IF(@has_fault_party > 0, 'SELECT 1',
    'ALTER TABLE complaints ADD COLUMN fault_party VARCHAR(25) NOT NULL DEFAULT ''SHOP''');
PREPARE add_fault_party_stmt FROM @add_fault_party_sql;
EXECUTE add_fault_party_stmt;
DEALLOCATE PREPARE add_fault_party_stmt;

SET @has_freeship_voucher = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'complaints'
      AND COLUMN_NAME = 'gifted_freeship_voucher'
);
SET @add_freeship_voucher_sql = IF(@has_freeship_voucher > 0, 'SELECT 1',
    'ALTER TABLE complaints ADD COLUMN gifted_freeship_voucher VARCHAR(50) NULL');
PREPARE add_freeship_voucher_stmt FROM @add_freeship_voucher_sql;
EXECUTE add_freeship_voucher_stmt;
DEALLOCATE PREPARE add_freeship_voucher_stmt;

-- 3. Create shop_reputation_logs table
CREATE TABLE IF NOT EXISTS shop_reputation_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    complaint_id BIGINT NULL,
    points_change INT NOT NULL,
    current_reputation INT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_shop_rep_shop_id (shop_id),
    INDEX idx_shop_rep_complaint_id (complaint_id)
);
