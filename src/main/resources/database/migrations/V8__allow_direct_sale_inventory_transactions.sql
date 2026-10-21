-- Shop confirmation can record a sale directly without first reserving stock.
SET @sale_check = (
    SELECT tc.CONSTRAINT_NAME
    FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc
      ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
     AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
      AND tc.TABLE_NAME = 'inventory_transactions'
      AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND cc.CHECK_CLAUSE LIKE '%SALE%reserved_delta%'
    LIMIT 1
);
SET @drop_sale_check = IF(
    @sale_check IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE inventory_transactions DROP CHECK `', REPLACE(@sale_check, '`', '``'), '`')
);
PREPARE drop_sale_check_stmt FROM @drop_sale_check;
EXECUTE drop_sale_check_stmt;
DEALLOCATE PREPARE drop_sale_check_stmt;

ALTER TABLE inventory_transactions
    ADD CONSTRAINT chk_inventory_transaction_deltas
    CHECK ((type = 'RECEIVE' AND on_hand_delta > 0 AND reserved_delta = 0)
        OR (type = 'ADJUST' AND on_hand_delta <> 0 AND reserved_delta = 0)
        OR (type = 'RESERVE' AND on_hand_delta = 0 AND reserved_delta > 0 AND order_item_id IS NOT NULL)
        OR (type = 'RELEASE' AND on_hand_delta = 0 AND reserved_delta < 0 AND order_item_id IS NOT NULL)
        OR (type = 'SALE' AND on_hand_delta < 0 AND reserved_delta IN (0, on_hand_delta) AND order_item_id IS NOT NULL)
        OR (type = 'DISCARD' AND on_hand_delta < 0 AND reserved_delta = 0));
