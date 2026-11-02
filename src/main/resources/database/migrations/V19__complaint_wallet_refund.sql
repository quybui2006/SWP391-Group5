-- =============================================================================
-- Migration V19: Complaint Real Wallet Refund & Customer Wallet Tracking
-- =============================================================================

-- 1. Đảm bảo bảng customer_wallets tồn tại
CREATE TABLE IF NOT EXISTS customer_wallets (
    user_id BIGINT PRIMARY KEY,
    balance DECIMAL(19,0) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    total_deposited DECIMAL(19,0) NOT NULL DEFAULT 0 CHECK (total_deposited >= 0),
    total_refunded DECIMAL(19,0) NOT NULL DEFAULT 0 CHECK (total_refunded >= 0),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 2. Đảm bảo bảng customer_wallet_transactions tồn tại
CREATE TABLE IF NOT EXISTS customer_wallet_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('DEPOSIT','REFUND','WITHDRAWAL','PAYMENT')),
    amount DECIMAL(19,0) NOT NULL CHECK (amount > 0),
    description VARCHAR(500) NULL,
    event_key VARCHAR(150) NOT NULL UNIQUE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 3. Bổ sung các cột theo dõi hoàn tiền vào bảng complaints
SET @has_refund_method = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'complaints'
      AND COLUMN_NAME = 'refund_method'
);
SET @add_refund_method_sql = IF(@has_refund_method > 0, 'SELECT 1',
    'ALTER TABLE complaints ADD COLUMN refund_method VARCHAR(25) NOT NULL DEFAULT ''WALLET'' AFTER fault_party');
PREPARE add_refund_method_stmt FROM @add_refund_method_sql;
EXECUTE add_refund_method_stmt;
DEALLOCATE PREPARE add_refund_method_stmt;

SET @has_refund_status = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'complaints'
      AND COLUMN_NAME = 'refund_status'
);
SET @add_refund_status_sql = IF(@has_refund_status > 0, 'SELECT 1',
    'ALTER TABLE complaints ADD COLUMN refund_status VARCHAR(25) NOT NULL DEFAULT ''NONE'' AFTER refund_method');
PREPARE add_refund_status_stmt FROM @add_refund_status_sql;
EXECUTE add_refund_status_stmt;
DEALLOCATE PREPARE add_refund_status_stmt;

SET @has_refund_completed_at = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'complaints'
      AND COLUMN_NAME = 'refund_completed_at'
);
SET @add_refund_completed_at_sql = IF(@has_refund_completed_at > 0, 'SELECT 1',
    'ALTER TABLE complaints ADD COLUMN refund_completed_at DATETIME(3) NULL AFTER refund_status');
PREPARE add_refund_completed_at_stmt FROM @add_refund_completed_at_sql;
EXECUTE add_refund_completed_at_stmt;
DEALLOCATE PREPARE add_refund_completed_at_stmt;

-- 4. Đồng bộ dữ liệu cũ: Các khiếu nại RESOLVED có tiền hoàn được đánh dấu COMPLETED
UPDATE complaints
SET refund_status = 'COMPLETED',
    refund_method = 'WALLET',
    refund_completed_at = resolved_at
WHERE status = 'RESOLVED' AND (final_refund_amount > 0 OR compensated_points > 0);

-- 5. Gỡ bỏ triệt để các CHECK constraint cũ trên MySQL gây lỗi Check constraint 'orders_chk_19' is violated
-- Drop tất cả các check constraint trên bảng orders có liên quan tới shop_revenue_amount
DROP PROCEDURE IF EXISTS drop_order_revenue_chk_constraints;
DELIMITER $$
CREATE PROCEDURE drop_order_revenue_chk_constraints()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE c_name VARCHAR(128);
    DECLARE cur CURSOR FOR
        SELECT tc.CONSTRAINT_NAME
        FROM information_schema.TABLE_CONSTRAINTS tc
        JOIN information_schema.CHECK_CONSTRAINTS cc
          ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
         AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
        WHERE tc.TABLE_SCHEMA = DATABASE()
          AND tc.TABLE_NAME = 'orders'
          AND tc.CONSTRAINT_TYPE = 'CHECK'
          AND cc.CHECK_CLAUSE LIKE '%shop_revenue_amount%'
          AND cc.CHECK_CLAUSE NOT LIKE '%>= 0%';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO c_name;
        IF done THEN
            LEAVE read_loop;
        END IF;
        SET @drop_stmt = CONCAT('ALTER TABLE orders DROP CHECK `', c_name, '`');
        PREPARE s FROM @drop_stmt;
        EXECUTE s;
        DEALLOCATE PREPARE s;
    END LOOP;
    CLOSE cur;
END$$
DELIMITER ;
CALL drop_order_revenue_chk_constraints();
DROP PROCEDURE IF EXISTS drop_order_revenue_chk_constraints;
