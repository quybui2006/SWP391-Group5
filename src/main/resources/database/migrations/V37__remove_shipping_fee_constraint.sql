-- Remove CHECK constraint forcing orders.shipping_fee to equal 20000.
-- TABLE_NAME belongs to INFORMATION_SCHEMA.TABLE_CONSTRAINTS,
-- not INFORMATION_SCHEMA.CHECK_CONSTRAINTS.

-- The previous failed run may have created this procedure before failing,
-- so drop it first to make this migration rerunnable during development.
DROP PROCEDURE IF EXISTS DropShippingFeeConstraint;

DELIMITER //
CREATE PROCEDURE DropShippingFeeConstraint()
BEGIN
    DECLARE v_constraint_name VARCHAR(64) DEFAULT NULL;

    -- A SELECT ... INTO that finds no row raises NOT FOUND.
    DECLARE CONTINUE HANDLER FOR NOT FOUND
        SET v_constraint_name = NULL;

SELECT cc.CONSTRAINT_NAME
INTO v_constraint_name
FROM information_schema.CHECK_CONSTRAINTS cc
         JOIN information_schema.TABLE_CONSTRAINTS tc
              ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA
                  AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
WHERE tc.TABLE_SCHEMA = DATABASE()
  AND tc.TABLE_NAME = 'orders'
  AND tc.CONSTRAINT_TYPE = 'CHECK'
  AND LOWER(REPLACE(cc.CHECK_CLAUSE, '`', ''))
    REGEXP 'shipping_fee[[:space:]]*=[[:space:]]*20000'
     LIMIT 1;

IF v_constraint_name IS NOT NULL THEN
        SET @drop_shipping_fee_sql = CONCAT(
            'ALTER TABLE `orders` DROP CHECK `',
            REPLACE(v_constraint_name, '`', '``'),
            '`'
        );

PREPARE drop_shipping_fee_stmt FROM @drop_shipping_fee_sql;
EXECUTE drop_shipping_fee_stmt;
DEALLOCATE PREPARE drop_shipping_fee_stmt;
END IF;
END //
DELIMITER ;

CALL DropShippingFeeConstraint();
DROP PROCEDURE IF EXISTS DropShippingFeeConstraint;

-- Set the default to 0 without changing the column's existing type/nullability.
ALTER TABLE orders ALTER COLUMN shipping_fee SET DEFAULT 0;