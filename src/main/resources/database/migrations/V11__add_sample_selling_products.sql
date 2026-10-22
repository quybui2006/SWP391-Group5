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

-- 2. Thêm sản phẩm mẫu đang mở bán (ACTIVE) cho tất cả Shop đã được duyệt mở bán
-- Shop 2 hoặc Shop đầu tiên
SET @shop_id = (SELECT id FROM shops WHERE id = 2 LIMIT 1);
SET @owner_id = (SELECT owner_id FROM shops WHERE id = @shop_id LIMIT 1);

-- Nếu không có shop id=2, lấy shop đầu tiên được duyệt
SET @shop_id = COALESCE(@shop_id, (SELECT id FROM shops WHERE approval_status = 'APPROVED' LIMIT 1));
SET @owner_id = COALESCE(@owner_id, (SELECT owner_id FROM shops WHERE id = @shop_id LIMIT 1));

-- Thêm Sản phẩm 1: Táo Envy New Zealand
INSERT INTO products (shop_id, name, description, origin, batch_code, is_visible, approval_status, selling_status, submitted_at, reviewed_at, created_at, updated_at)
SELECT @shop_id, 'Táo Envy New Zealand Size 24', 'Táo Envy nhập khẩu trực tiếp từ New Zealand, giòn ngọt đậm đà, vỏ đỏ mọng tự nhiên.', 'New Zealand', 'BATCH-2026-AP01', 1, 'APPROVED', 'ACTIVE', NOW(3), NOW(3), NOW(3), NOW(3)
WHERE @shop_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM products WHERE shop_id = @shop_id AND batch_code = 'BATCH-2026-AP01');

SET @p1_id = (SELECT id FROM products WHERE shop_id = @shop_id AND batch_code = 'BATCH-2026-AP01' LIMIT 1);

-- Thêm Sản phẩm 2: Nho mẫu đơn Shine Muscat
INSERT INTO products (shop_id, name, description, origin, batch_code, is_visible, approval_status, selling_status, submitted_at, reviewed_at, created_at, updated_at)
SELECT @shop_id, 'Nho mẫu đơn Shine Muscat Hàn Quốc', 'Nho mẫu đơn chuẩn xuất khẩu, quả to tròn không hạt, hương thơm sữa ngọt ngào.', 'Hàn Quốc', 'BATCH-2026-GR01', 1, 'APPROVED', 'ACTIVE', NOW(3), NOW(3), NOW(3), NOW(3)
WHERE @shop_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM products WHERE shop_id = @shop_id AND batch_code = 'BATCH-2026-GR01');

SET @p2_id = (SELECT id FROM products WHERE shop_id = @shop_id AND batch_code = 'BATCH-2026-GR01' LIMIT 1);

-- Thêm Sản phẩm 3: Dâu tây New Sky Đà Lạt
INSERT INTO products (shop_id, name, description, origin, batch_code, is_visible, approval_status, selling_status, submitted_at, reviewed_at, created_at, updated_at)
SELECT @shop_id, 'Dâu tây New Sky Đà Lạt giống Nhật', 'Dâu tây trồng nhà kính theo tiêu chuẩn VietGAP tại Đà Lạt, quả mọng đỏ ngọt thanh.', 'Lâm Đồng, Việt Nam', 'BATCH-2026-SB01', 1, 'APPROVED', 'ACTIVE', NOW(3), NOW(3), NOW(3), NOW(3)
WHERE @shop_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM products WHERE shop_id = @shop_id AND batch_code = 'BATCH-2026-SB01');

SET @p3_id = (SELECT id FROM products WHERE shop_id = @shop_id AND batch_code = 'BATCH-2026-SB01' LIMIT 1);

-- Thêm Sản phẩm 4: Cam vàng Navel Úc
INSERT INTO products (shop_id, name, description, origin, batch_code, is_visible, approval_status, selling_status, submitted_at, reviewed_at, created_at, updated_at)
SELECT @shop_id, 'Cam vàng Navel Úc không hạt', 'Cam vàng Navel mọng nước, nhiều vitamin C, vị ngọt thanh tự nhiên.', 'Úc (Australia)', 'BATCH-2026-OR01', 1, 'APPROVED', 'ACTIVE', NOW(3), NOW(3), NOW(3), NOW(3)
WHERE @shop_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM products WHERE shop_id = @shop_id AND batch_code = 'BATCH-2026-OR01');

SET @p4_id = (SELECT id FROM products WHERE shop_id = @shop_id AND batch_code = 'BATCH-2026-OR01' LIMIT 1);

-- 3. Gắn danh mục cho các sản phẩm
SET @cat_nhapkhau = (SELECT id FROM categories WHERE name = 'Trái cây nhập khẩu' LIMIT 1);
SET @cat_nho_dau = (SELECT id FROM categories WHERE name = 'Nho & Dâu tây' LIMIT 1);
SET @cat_nhietdoi = (SELECT id FROM categories WHERE name = 'Trái cây nhiệt đới' LIMIT 1);
SET @cat_dalat = (SELECT id FROM categories WHERE name = 'Trái cây Đà Lạt' LIMIT 1);

INSERT IGNORE INTO product_categories (product_id, category_id)
SELECT @p1_id, @cat_nhapkhau WHERE @p1_id IS NOT NULL AND @cat_nhapkhau IS NOT NULL;

INSERT IGNORE INTO product_categories (product_id, category_id)
SELECT @p2_id, @cat_nho_dau WHERE @p2_id IS NOT NULL AND @cat_nho_dau IS NOT NULL;

INSERT IGNORE INTO product_categories (product_id, category_id)
SELECT @p3_id, @cat_dalat WHERE @p3_id IS NOT NULL AND @cat_dalat IS NOT NULL;

INSERT IGNORE INTO product_categories (product_id, category_id)
SELECT @p4_id, @cat_nhapkhau WHERE @p4_id IS NOT NULL AND @cat_nhapkhau IS NOT NULL;

-- 4. Thêm ảnh đại diện sản phẩm
INSERT IGNORE INTO product_images (product_id, image_url, sort_order)
SELECT @p1_id, 'https://images.unsplash.com/photo-1560806887-1e4cd0b6cbd6?w=600&auto=format&fit=crop&q=80', 0
WHERE @p1_id IS NOT NULL;

INSERT IGNORE INTO product_images (product_id, image_url, sort_order)
SELECT @p2_id, 'https://images.unsplash.com/photo-1596363505729-4190a9506133?w=600&auto=format&fit=crop&q=80', 0
WHERE @p2_id IS NOT NULL;

INSERT IGNORE INTO product_images (product_id, image_url, sort_order)
SELECT @p3_id, 'https://images.unsplash.com/photo-1464965911861-746a04b4bca6?w=600&auto=format&fit=crop&q=80', 0
WHERE @p3_id IS NOT NULL;

INSERT IGNORE INTO product_images (product_id, image_url, sort_order)
SELECT @p4_id, 'https://images.unsplash.com/photo-1611080626919-7cf5a9dbab5b?w=600&auto=format&fit=crop&q=80', 0
WHERE @p4_id IS NOT NULL;

-- 5. Thêm biến thể sản phẩm (Product Variants) đang mở bán (status = 'ACTIVE', approval_status = 'APPROVED')
SET @unit_kg = (SELECT id FROM units WHERE code = 'KG' LIMIT 1);
SET @unit_box = (SELECT id FROM units WHERE code = 'BOX' LIMIT 1);
SET @unit_carton = (SELECT id FROM units WHERE code = 'CARTON' LIMIT 1);

-- Biến thể 1: Táo Envy Hộp 1kg
INSERT INTO product_variants (product_id, shop_id, sku, name, specification, image_url, base_unit_id, price, min_order_quantity, status, approval_status, submitted_at, reviewed_at, created_at, updated_at)
SELECT @p1_id, @shop_id, 'AP-ENVY-1KG', 'Hộp 1kg (3 - 4 quả)', 'Đóng hộp cao cấp khoảng 1kg, 3 đến 4 quả chọn lọc.', 'https://images.unsplash.com/photo-1560806887-1e4cd0b6cbd6?w=600&auto=format&fit=crop&q=80', @unit_kg, 180000, 1, 'ACTIVE', 'APPROVED', NOW(3), NOW(3), NOW(3), NOW(3)
WHERE @p1_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM product_variants WHERE shop_id = @shop_id AND sku = 'AP-ENVY-1KG');

SET @v1_id = (SELECT id FROM product_variants WHERE shop_id = @shop_id AND sku = 'AP-ENVY-1KG' LIMIT 1);

-- Biến thể 2: Táo Envy Thùng 5kg
INSERT INTO product_variants (product_id, shop_id, sku, name, specification, image_url, base_unit_id, price, min_order_quantity, status, approval_status, submitted_at, reviewed_at, created_at, updated_at)
SELECT @p1_id, @shop_id, 'AP-ENVY-5KG', 'Thùng nguyên 5kg', 'Thùng nguyên đai nguyên kiện nhập khẩu 5kg.', 'https://images.unsplash.com/photo-1560806887-1e4cd0b6cbd6?w=600&auto=format&fit=crop&q=80', @unit_carton, 850000, 1, 'ACTIVE', 'APPROVED', NOW(3), NOW(3), NOW(3), NOW(3)
WHERE @p1_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM product_variants WHERE shop_id = @shop_id AND sku = 'AP-ENVY-5KG');

SET @v2_id = (SELECT id FROM product_variants WHERE shop_id = @shop_id AND sku = 'AP-ENVY-5KG' LIMIT 1);

-- Biến thể 3: Nho Shine Muscat Hộp 500g
INSERT INTO product_variants (product_id, shop_id, sku, name, specification, image_url, base_unit_id, price, min_order_quantity, status, approval_status, submitted_at, reviewed_at, created_at, updated_at)
SELECT @p2_id, @shop_id, 'GR-SHINE-500G', 'Chùm 500g hộp quà VIP', 'Chùm nguyên vẹn đóng hộp giấy sang trọng có quai xách.', 'https://images.unsplash.com/photo-1596363505729-4190a9506133?w=600&auto=format&fit=crop&q=80', @unit_box, 320000, 1, 'ACTIVE', 'APPROVED', NOW(3), NOW(3), NOW(3), NOW(3)
WHERE @p2_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM product_variants WHERE shop_id = @shop_id AND sku = 'GR-SHINE-500G');

SET @v3_id = (SELECT id FROM product_variants WHERE shop_id = @shop_id AND sku = 'GR-SHINE-500G' LIMIT 1);

-- Biến thể 4: Dâu tây New Sky Hộp 500g
INSERT INTO product_variants (product_id, shop_id, sku, name, specification, image_url, base_unit_id, price, min_order_quantity, status, approval_status, submitted_at, reviewed_at, created_at, updated_at)
SELECT @p3_id, @shop_id, 'SB-DALAT-500G', 'Hộp 500g (20 - 24 quả)', 'Hộp nhựa thông hơi giữ tươi, quả chín đỏ tự nhiên.', 'https://images.unsplash.com/photo-1464965911861-746a04b4bca6?w=600&auto=format&fit=crop&q=80', @unit_box, 195000, 1, 'ACTIVE', 'APPROVED', NOW(3), NOW(3), NOW(3), NOW(3)
WHERE @p3_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM product_variants WHERE shop_id = @shop_id AND sku = 'SB-DALAT-500G');

SET @v4_id = (SELECT id FROM product_variants WHERE shop_id = @shop_id AND sku = 'SB-DALAT-500G' LIMIT 1);

-- Biến thể 5: Cam Navel Túi 1kg
INSERT INTO product_variants (product_id, shop_id, sku, name, specification, image_url, base_unit_id, price, min_order_quantity, status, approval_status, submitted_at, reviewed_at, created_at, updated_at)
SELECT @p4_id, @shop_id, 'OR-NAVEL-1KG', 'Túi lưới 1kg', 'Túi lưới 1kg cam vàng chọn lọc, vỏ bóng đẹp.', 'https://images.unsplash.com/photo-1611080626919-7cf5a9dbab5b?w=600&auto=format&fit=crop&q=80', @unit_kg, 95000, 1, 'ACTIVE', 'APPROVED', NOW(3), NOW(3), NOW(3), NOW(3)
WHERE @p4_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM product_variants WHERE shop_id = @shop_id AND sku = 'OR-NAVEL-1KG');

SET @v5_id = (SELECT id FROM product_variants WHERE shop_id = @shop_id AND sku = 'OR-NAVEL-1KG' LIMIT 1);

-- 6. Cập nhật tồn kho ban đầu (Variant Inventory)
INSERT INTO variant_inventory (variant_id, initial_quantity, quantity_on_hand, reserved_quantity, low_stock_threshold_pct, cost_per_base_unit, status, created_by, created_at)
SELECT @v1_id, 100, 85, 0, 10.00, 140000, 'ACTIVE', @owner_id, NOW(3)
WHERE @v1_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM variant_inventory WHERE variant_id = @v1_id);

INSERT INTO variant_inventory (variant_id, initial_quantity, quantity_on_hand, reserved_quantity, low_stock_threshold_pct, cost_per_base_unit, status, created_by, created_at)
SELECT @v2_id, 50, 42, 0, 10.00, 680000, 'ACTIVE', @owner_id, NOW(3)
WHERE @v2_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM variant_inventory WHERE variant_id = @v2_id);

INSERT INTO variant_inventory (variant_id, initial_quantity, quantity_on_hand, reserved_quantity, low_stock_threshold_pct, cost_per_base_unit, status, created_by, created_at)
SELECT @v3_id, 80, 68, 0, 10.00, 250000, 'ACTIVE', @owner_id, NOW(3)
WHERE @v3_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM variant_inventory WHERE variant_id = @v3_id);

INSERT INTO variant_inventory (variant_id, initial_quantity, quantity_on_hand, reserved_quantity, low_stock_threshold_pct, cost_per_base_unit, status, created_by, created_at)
SELECT @v4_id, 60, 50, 0, 10.00, 150000, 'ACTIVE', @owner_id, NOW(3)
WHERE @v4_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM variant_inventory WHERE variant_id = @v4_id);

INSERT INTO variant_inventory (variant_id, initial_quantity, quantity_on_hand, reserved_quantity, low_stock_threshold_pct, cost_per_base_unit, status, created_by, created_at)
SELECT @v5_id, 150, 130, 0, 10.00, 70000, 'ACTIVE', @owner_id, NOW(3)
WHERE @v5_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM variant_inventory WHERE variant_id = @v5_id);
