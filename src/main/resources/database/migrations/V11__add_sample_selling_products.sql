-- =============================================================================
-- Migration V11: Add Sample Selling Products & Categories for Shop Owners
-- =============================================================================

-- 1. Thêm danh mục hoa quả nếu chưa có
INSERT IGNORE INTO categories (name, is_active) VALUES
('Trái cây nhiệt đới', 1),
('Trái cây nhập khẩu', 1),
('Trái cây Đà Lạt', 1),
('Nho & Dâu tây', 1),
('Hộp & Giỏ quà trái cây', 1);

