-- Migration V19: Thêm Giấy phép kinh doanh và Giấy đảm bảo vệ sinh an toàn thực phẩm cho Shops
ALTER TABLE shops 
    ADD COLUMN business_license_number VARCHAR(100) NULL,
    ADD COLUMN business_license_date DATE NULL,
    ADD COLUMN business_license_place VARCHAR(255) NULL,
    ADD COLUMN business_license_url VARCHAR(500) NULL,
    ADD COLUMN food_safety_cert_number VARCHAR(100) NULL,
    ADD COLUMN food_safety_cert_date DATE NULL,
    ADD COLUMN food_safety_cert_expiry DATE NULL,
    ADD COLUMN food_safety_cert_place VARCHAR(255) NULL,
    ADD COLUMN food_safety_cert_url VARCHAR(500) NULL;

