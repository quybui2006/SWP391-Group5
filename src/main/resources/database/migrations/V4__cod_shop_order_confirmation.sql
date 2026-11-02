-- COD orders stay unreserved until the Shop Owner confirms them.
SET @allocation_check = (
    SELECT tc.CONSTRAINT_NAME
    FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc
      ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
     AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
      AND tc.TABLE_NAME = 'order_items'
      AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND cc.CHECK_CLAUSE LIKE '%allocation_status%'
    LIMIT 1
);
SET @drop_allocation_check = IF(
    @allocation_check IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE order_items DROP CHECK `', REPLACE(@allocation_check, '`', '``'), '`')
);
PREPARE drop_allocation_check_stmt FROM @drop_allocation_check;
EXECUTE drop_allocation_check_stmt;
DEALLOCATE PREPARE drop_allocation_check_stmt;

ALTER TABLE order_items
    ADD CONSTRAINT chk_order_items_allocation_status
    CHECK (allocation_status IN ('UNRESERVED','RESERVED','CONSUMED','RELEASED'));
