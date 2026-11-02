-- Convert the old percentage to a count in the variant's selling unit.
SET @has_quantity_threshold = (
    SELECT COLUMN_NAME FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'variant_inventory'
      AND COLUMN_NAME = 'low_stock_threshold_quantity'
    LIMIT 1
);
SET @add_quantity_threshold = IF(
    @has_quantity_threshold IS NULL,
    'ALTER TABLE variant_inventory ADD COLUMN low_stock_threshold_quantity INT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE add_quantity_threshold_stmt FROM @add_quantity_threshold;
EXECUTE add_quantity_threshold_stmt;
DEALLOCATE PREPARE add_quantity_threshold_stmt;

SET @has_percent_threshold = (
    SELECT COLUMN_NAME FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'variant_inventory'
      AND COLUMN_NAME = 'low_stock_threshold_pct'
    LIMIT 1
);
SET @convert_percent_threshold = IF(
    @has_percent_threshold IS NULL,
    'SELECT 1',
    'UPDATE variant_inventory SET low_stock_threshold_quantity = CEIL(initial_quantity * low_stock_threshold_pct / 100)'
);
PREPARE convert_percent_threshold_stmt FROM @convert_percent_threshold;
EXECUTE convert_percent_threshold_stmt;
DEALLOCATE PREPARE convert_percent_threshold_stmt;

SET @percent_threshold_check = (
    SELECT tc.CONSTRAINT_NAME
    FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc
      ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
     AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.CONSTRAINT_SCHEMA = DATABASE() AND tc.TABLE_NAME = 'variant_inventory'
      AND tc.CONSTRAINT_TYPE = 'CHECK' AND cc.CHECK_CLAUSE LIKE '%low_stock_threshold_pct%'
    LIMIT 1
);
SET @drop_percent_threshold_check = IF(
    @percent_threshold_check IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE variant_inventory DROP CHECK `', REPLACE(@percent_threshold_check, '`', '``'), '`')
);
PREPARE drop_percent_threshold_check_stmt FROM @drop_percent_threshold_check;
EXECUTE drop_percent_threshold_check_stmt;
DEALLOCATE PREPARE drop_percent_threshold_check_stmt;

SET @drop_percent_threshold = IF(
    @has_percent_threshold IS NULL,
    'SELECT 1',
    'ALTER TABLE variant_inventory DROP COLUMN low_stock_threshold_pct'
);
PREPARE drop_percent_threshold_stmt FROM @drop_percent_threshold;
EXECUTE drop_percent_threshold_stmt;
DEALLOCATE PREPARE drop_percent_threshold_stmt;

SET @quantity_threshold_check = (
    SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'variant_inventory'
      AND CONSTRAINT_TYPE = 'CHECK' AND CONSTRAINT_NAME = 'CK_variant_inventory_low_stock_threshold'
    LIMIT 1
);
SET @add_quantity_threshold_check = IF(
    @quantity_threshold_check IS NULL,
    'ALTER TABLE variant_inventory ADD CONSTRAINT CK_variant_inventory_low_stock_threshold CHECK (low_stock_threshold_quantity >= 0)',
    'SELECT 1'
);
PREPARE add_quantity_threshold_check_stmt FROM @add_quantity_threshold_check;
EXECUTE add_quantity_threshold_check_stmt;
DEALLOCATE PREPARE add_quantity_threshold_check_stmt;
