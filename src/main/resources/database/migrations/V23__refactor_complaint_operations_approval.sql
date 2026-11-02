-- =============================================================================
-- Migration V23: Refactor Complaint Flow - Operations Approval First
-- =============================================================================

-- Bổ sung các cột phục vụ ghi nhận kết quả thẩm định của Ban Vận hành
SET @has_ops_decision = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'complaints'
      AND COLUMN_NAME = 'operations_decision'
);
SET @alter_c_ops_dec = IF(@has_ops_decision > 0, 'SELECT 1',
    'ALTER TABLE complaints ADD COLUMN operations_decision VARCHAR(25) NULL AFTER shop_responded_at');
PREPARE stmt_c_opsdec FROM @alter_c_ops_dec;
EXECUTE stmt_c_opsdec;
DEALLOCATE PREPARE stmt_c_opsdec;

SET @has_ops_note = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'complaints'
      AND COLUMN_NAME = 'operations_note'
);
SET @alter_c_ops_note = IF(@has_ops_note > 0, 'SELECT 1',
    'ALTER TABLE complaints ADD COLUMN operations_note VARCHAR(1500) NULL AFTER operations_decision');
PREPARE stmt_c_opsnote FROM @alter_c_ops_note;
EXECUTE stmt_c_opsnote;
DEALLOCATE PREPARE stmt_c_opsnote;

SET @has_ops_reviewed_at = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'complaints'
      AND COLUMN_NAME = 'operations_reviewed_at'
);
SET @alter_c_ops_rev = IF(@has_ops_reviewed_at > 0, 'SELECT 1',
    'ALTER TABLE complaints ADD COLUMN operations_reviewed_at DATETIME(3) NULL AFTER operations_note');
PREPARE stmt_c_opsrev FROM @alter_c_ops_rev;
EXECUTE stmt_c_opsrev;
DEALLOCATE PREPARE stmt_c_opsrev;
