-- Migration V23: Thêm Giấy phép kinh doanh và Giấy đảm bảo vệ sinh an toàn thực phẩm cho Shops
SET @has_bln = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'shops'
      AND COLUMN_NAME = 'business_license_number'
);
SET @sql_v23 = IF(@has_bln > 0, 'SELECT 1',
    'ALTER TABLE shops 
        ADD COLUMN business_license_number VARCHAR(100) NULL,
        ADD COLUMN business_license_date DATE NULL,
        ADD COLUMN business_license_place VARCHAR(255) NULL,
        ADD COLUMN business_license_url VARCHAR(500) NULL,
        ADD COLUMN food_safety_cert_number VARCHAR(100) NULL,
        ADD COLUMN food_safety_cert_date DATE NULL,
        ADD COLUMN food_safety_cert_expiry DATE NULL,
        ADD COLUMN food_safety_cert_place VARCHAR(255) NULL,
        ADD COLUMN food_safety_cert_url VARCHAR(500) NULL'
);
PREPARE stmt_v23 FROM @sql_v23;
EXECUTE stmt_v23;
DEALLOCATE PREPARE stmt_v23;
