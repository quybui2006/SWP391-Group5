ALTER TABLE products
    ADD COLUMN is_visible TINYINT(1) NOT NULL DEFAULT 1 AFTER batch_code;