-- One-time migration for databases that already ran the variant approval migration.
-- A pending listing is visible for sale while Operations performs post-moderation.
UPDATE product_variants
SET status = 'ACTIVE'
WHERE approval_status = 'PENDING' AND status = 'DRAFT';

SET @add_suspension_reason = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shops' AND COLUMN_NAME = 'suspension_reason') = 0,
    'ALTER TABLE shops ADD COLUMN suspension_reason VARCHAR(1000) NULL AFTER reviewed_at',
    'SELECT 1'
);
PREPARE ff_migration_stmt FROM @add_suspension_reason;
EXECUTE ff_migration_stmt;
DEALLOCATE PREPARE ff_migration_stmt;

SET @add_notification_created_at = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notifications' AND COLUMN_NAME = 'created_at') = 0,
    'ALTER TABLE notifications ADD COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)',
    'SELECT 1'
);
PREPARE ff_migration_stmt FROM @add_notification_created_at;
EXECUTE ff_migration_stmt;
DEALLOCATE PREPARE ff_migration_stmt;

CREATE TABLE IF NOT EXISTS shop_reopen_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    explanation VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    operation_reason VARCHAR(1000) NULL,
    requested_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    reviewed_at DATETIME(3) NULL,
    CONSTRAINT FK_shop_reopen_shop FOREIGN KEY (shop_id) REFERENCES shops(id),
    CONSTRAINT FK_shop_reopen_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT CHK_shop_reopen_status CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    CONSTRAINT CHK_shop_reopen_explanation CHECK (CHAR_LENGTH(TRIM(explanation)) >= 10),
    CONSTRAINT CHK_shop_reopen_reason CHECK (status <> 'REJECTED' OR (operation_reason IS NOT NULL AND CHAR_LENGTH(TRIM(operation_reason)) >= 5))
);
CREATE INDEX IX_shop_reopen_requests_status ON shop_reopen_requests(status, requested_at);
