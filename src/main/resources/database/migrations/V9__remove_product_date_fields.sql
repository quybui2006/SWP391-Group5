-- Remove product dates and the historical expiry snapshot from order items.
-- The check constraint has an engine-generated name, so discover it by clause.
SET @product_date_check = (
    SELECT tc.CONSTRAINT_NAME
    FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc
      ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
     AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
      AND tc.TABLE_NAME = 'products'
      AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND (cc.CHECK_CLAUSE LIKE '%expiry_date%'
           OR cc.CHECK_CLAUSE LIKE '%received_date%')
    LIMIT 1
);
SET @drop_product_date_check = IF(
    @product_date_check IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE products DROP CHECK `', REPLACE(@product_date_check, '`', '``'), '`')
);
PREPARE drop_product_date_check_stmt FROM @drop_product_date_check;
EXECUTE drop_product_date_check_stmt;
DEALLOCATE PREPARE drop_product_date_check_stmt;

ALTER TABLE products
    DROP INDEX IX_products_expiry,
    DROP COLUMN received_date,
    DROP COLUMN expiry_date;

ALTER TABLE order_items
    DROP COLUMN expiry_date;
