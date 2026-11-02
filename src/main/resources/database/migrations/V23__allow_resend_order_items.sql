-- =============================================================================
-- Migration V21: Allow Zero-Price Items for Resend/Replacement Orders
-- =============================================================================

-- 1. Gỡ bỏ check constraints trên bảng order_items ép buộc unit_price > 0 và sale_unit_price > 0
DROP PROCEDURE IF EXISTS drop_order_items_price_chk_constraints;
DELIMITER $$
CREATE PROCEDURE drop_order_items_price_chk_constraints()
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
          AND tc.TABLE_NAME = 'order_items'
          AND tc.CONSTRAINT_TYPE = 'CHECK'
          AND (
              cc.CHECK_CLAUSE LIKE '%unit_price% > 0%'
              OR cc.CHECK_CLAUSE LIKE '%sale_unit_price% > 0%'
          );
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO c_name;
        IF done THEN
            LEAVE read_loop;
        END IF;
        SET @drop_stmt = CONCAT('ALTER TABLE order_items DROP CHECK `', c_name, '`');
        PREPARE s FROM @drop_stmt;
        EXECUTE s;
        DEALLOCATE PREPARE s;
    END LOOP;
    CLOSE cur;
END$$
DELIMITER ;
CALL drop_order_items_price_chk_constraints();
DROP PROCEDURE IF EXISTS drop_order_items_price_chk_constraints;

-- Thêm check constraint mới cho phép >= 0 (hỗ trợ đơn hàng giao bù 0đ)
SET @has_unit_price_chk = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'order_items'
      AND CONSTRAINT_NAME = 'chk_order_items_unit_price'
);
SET @add_unit_price_sql = IF(@has_unit_price_chk > 0, 'SELECT 1',
    'ALTER TABLE order_items ADD CONSTRAINT chk_order_items_unit_price CHECK (unit_price >= 0)');
PREPARE stmt_add_up FROM @add_unit_price_sql;
EXECUTE stmt_add_up;
DEALLOCATE PREPARE stmt_add_up;

SET @has_sale_unit_price_chk = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'order_items'
      AND CONSTRAINT_NAME = 'chk_order_items_sale_unit_price'
);
SET @add_sale_unit_price_sql = IF(@has_sale_unit_price_chk > 0, 'SELECT 1',
    'ALTER TABLE order_items ADD CONSTRAINT chk_order_items_sale_unit_price CHECK (sale_unit_price >= 0)');
PREPARE stmt_add_sup FROM @add_sale_unit_price_sql;
EXECUTE stmt_add_sup;
DEALLOCATE PREPARE stmt_add_sup;


-- 2. Gỡ bỏ check constraint trên bảng payments và hỗ trợ phương thức RESEND
DROP PROCEDURE IF EXISTS drop_payments_method_chk_constraints;
DELIMITER $$
CREATE PROCEDURE drop_payments_method_chk_constraints()
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
          AND tc.TABLE_NAME = 'payments'
          AND tc.CONSTRAINT_TYPE = 'CHECK'
          AND cc.CHECK_CLAUSE LIKE '%method%'
          AND cc.CHECK_CLAUSE NOT LIKE '%RESEND%';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO c_name;
        IF done THEN
            LEAVE read_loop;
        END IF;
        SET @drop_stmt = CONCAT('ALTER TABLE payments DROP CHECK `', c_name, '`');
        PREPARE s FROM @drop_stmt;
        EXECUTE s;
        DEALLOCATE PREPARE s;
    END LOOP;
    CLOSE cur;
END$$
DELIMITER ;
CALL drop_payments_method_chk_constraints();
DROP PROCEDURE IF EXISTS drop_payments_method_chk_constraints;

SET @has_pay_method_chk = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'payments'
      AND CONSTRAINT_NAME = 'chk_payments_method'
);
SET @add_pay_method_sql = IF(@has_pay_method_chk > 0, 'SELECT 1',
    'ALTER TABLE payments ADD CONSTRAINT chk_payments_method CHECK (method IN (''COD'', ''MOCK_ONLINE'', ''VNPAY'', ''RESEND''))');
PREPARE stmt_add_pm FROM @add_pay_method_sql;
EXECUTE stmt_add_pm;
DEALLOCATE PREPARE stmt_add_pm;
