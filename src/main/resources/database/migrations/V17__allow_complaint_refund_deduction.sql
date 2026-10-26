-- =============================================================================
-- Migration V17: Allow shop_revenue_amount deduction on orders for complaint refunds
-- =============================================================================

SET @revenue_check = (
    SELECT tc.CONSTRAINT_NAME
    FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc
      ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
     AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
      AND tc.TABLE_NAME = 'orders'
      AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND cc.CHECK_CLAUSE LIKE '%shop_revenue_amount%total_amount%'
    LIMIT 1
);
SET @drop_revenue_check = IF(@revenue_check IS NULL, 'SELECT 1',
    CONCAT('ALTER TABLE orders DROP CHECK `', REPLACE(@revenue_check, '`', '``'), '`'));
PREPARE drop_revenue_check_stmt FROM @drop_revenue_check;
EXECUTE drop_revenue_check_stmt;
DEALLOCATE PREPARE drop_revenue_check_stmt;

SET @revenue_subtotal_check = (
    SELECT tc.CONSTRAINT_NAME
    FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc
      ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
     AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
      AND tc.TABLE_NAME = 'orders'
      AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND cc.CHECK_CLAUSE LIKE '%shop_revenue_amount%subtotal%'
    LIMIT 1
);
SET @drop_rev_sub_check = IF(@revenue_subtotal_check IS NULL, 'SELECT 1',
    CONCAT('ALTER TABLE orders DROP CHECK `', REPLACE(@revenue_subtotal_check, '`', '``'), '`'));
PREPARE drop_rev_sub_stmt FROM @drop_rev_sub_check;
EXECUTE drop_rev_sub_stmt;
DEALLOCATE PREPARE drop_rev_sub_stmt;

-- Add check ensuring shop_revenue_amount is non-negative
SET @has_non_neg_check = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS tc
    WHERE tc.TABLE_SCHEMA = DATABASE()
      AND tc.TABLE_NAME = 'orders'
      AND tc.CONSTRAINT_NAME = 'chk_order_shop_revenue_non_negative'
);
SET @add_non_neg_sql = IF(@has_non_neg_check > 0, 'SELECT 1',
    'ALTER TABLE orders ADD CONSTRAINT chk_order_shop_revenue_non_negative CHECK (shop_revenue_amount >= 0)');
PREPARE add_non_neg_stmt FROM @add_non_neg_sql;
EXECUTE add_non_neg_stmt;
DEALLOCATE PREPARE add_non_neg_stmt;
