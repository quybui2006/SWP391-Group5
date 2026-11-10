-- Keep the complaints schema aligned with the Complaint entity.
SET @has_shop_remind_count = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'complaints'
      AND COLUMN_NAME = 'shop_remind_count'
);

SET @add_shop_remind_count = IF(@has_shop_remind_count > 0, 'SELECT 1',
    'ALTER TABLE complaints ADD COLUMN shop_remind_count INT NOT NULL DEFAULT 0');
PREPARE stmt_shop_remind_count FROM @add_shop_remind_count;
EXECUTE stmt_shop_remind_count;
DEALLOCATE PREPARE stmt_shop_remind_count;

SET @has_last_reminded_at = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'complaints'
      AND COLUMN_NAME = 'last_reminded_at'
);

SET @add_last_reminded_at = IF(@has_last_reminded_at > 0, 'SELECT 1',
    'ALTER TABLE complaints ADD COLUMN last_reminded_at DATETIME(3) NULL');
PREPARE stmt_last_reminded_at FROM @add_last_reminded_at;
EXECUTE stmt_last_reminded_at;
DEALLOCATE PREPARE stmt_last_reminded_at;

SET @has_cs_shop_request_note = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'complaints'
      AND COLUMN_NAME = 'cs_shop_request_note'
);

SET @add_cs_shop_request_note = IF(@has_cs_shop_request_note > 0, 'SELECT 1',
    'ALTER TABLE complaints ADD COLUMN cs_shop_request_note VARCHAR(1000) NULL');
PREPARE stmt_cs_shop_request_note FROM @add_cs_shop_request_note;
EXECUTE stmt_cs_shop_request_note;
DEALLOCATE PREPARE stmt_cs_shop_request_note;
