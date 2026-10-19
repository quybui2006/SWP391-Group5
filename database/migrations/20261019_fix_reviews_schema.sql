-- Fix bảng reviews cho khớp với entity Review (variant_id + user_id)
-- Bảng cũ tạo từ schema cũ (chỉ có order_item_id) -> gây lỗi
-- "Unknown column 'r1_0.user_id' in 'field list'" khi mở trang chi tiết sản phẩm.
-- Bảng hiện tại đang rỗng nên drop & tạo lại theo OnlineFruitShoppingPlatform.sql

DROP TABLE IF EXISTS reviews;

CREATE TABLE reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    variant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    order_item_id BIGINT NULL UNIQUE,
    rating TINYINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    content VARCHAR(2000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    FOREIGN KEY (variant_id) REFERENCES product_variants(id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (order_item_id) REFERENCES order_items(id)
);
