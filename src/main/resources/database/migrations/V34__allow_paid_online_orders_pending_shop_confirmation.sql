-- Keep successful online payments pending until the shop confirms the order.
-- The callback writes payment_status='PAID' while status remains 'PENDING'.
-- Reconcile legacy databases that still have an older cross-column CHECK
-- requiring a paid order to be confirmed immediately.

SET @legacy_paid_status_check = (
    SELECT tc.CONSTRAINT_NAME
    FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc
      ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
     AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
      AND tc.TABLE_NAME = 'orders'
      AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND (
          tc.CONSTRAINT_NAME = 'orders_chk_20'
          OR (
              cc.CHECK_CLAUSE LIKE '%payment_status%'
              AND cc.CHECK_CLAUSE LIKE '%status%'
              AND (cc.CHECK_CLAUSE LIKE '%PAID%' OR cc.CHECK_CLAUSE LIKE '%CONFIRMED%')
          )
      )
      AND cc.CHECK_CLAUSE NOT LIKE '%completed_at%'
      AND cc.CHECK_CLAUSE NOT LIKE '%CANCELLED%'
    ORDER BY tc.CONSTRAINT_NAME
    LIMIT 1
);

SET @drop_legacy_paid_status_check = IF(
    @legacy_paid_status_check IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE orders DROP CHECK `', REPLACE(@legacy_paid_status_check, '`', '``'), '`')
);
PREPARE drop_legacy_paid_status_check_stmt FROM @drop_legacy_paid_status_check;
EXECUTE drop_legacy_paid_status_check_stmt;
DEALLOCATE PREPARE drop_legacy_paid_status_check_stmt;

SELECT 1;
