-- Dùng Procedure để tìm đúng tên của Check Constraint đang ép giá 20.000đ và gỡ bỏ nó
DELIMITER //

CREATE PROCEDURE DropShippingFeeConstraint()
BEGIN
    DECLARE constraint_name VARCHAR(64);

    -- Tìm tên constraint liên quan đến shipping_fee trong bảng orders
SELECT CONSTRAINT_NAME INTO constraint_name
FROM information_schema.CHECK_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE()
  AND TABLE_NAME = 'orders'
  AND CHECK_CLAUSE LIKE '%shipping_fee = 20000%';

-- Nếu tìm thấy, thực thi lệnh DROP CHECK
IF constraint_name IS NOT NULL THEN
        SET @s = CONCAT('ALTER TABLE orders DROP CHECK ', constraint_name);
PREPARE stmt FROM @s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
END IF;
END //

DELIMITER ;

-- Gọi hàm để xóa constraint
CALL DropShippingFeeConstraint();

-- Xóa hàm sau khi dùng xong để dọn dẹp Database
DROP PROCEDURE DropShippingFeeConstraint();

-- Đổi lại Default của cột thành 0 (Tùy chọn, để an toàn hơn)
ALTER TABLE orders MODIFY COLUMN shipping_fee DECIMAL(19,0) NOT NULL DEFAULT 0;