-- FreshFruit - chuyển duyệt sản phẩm từ tiền kiểm sang hậu kiểm.
-- Chạy trên database hiện hữu được tạo từ FreshFruit.sql (constraint đã có tên).
-- Sau lệnh này, sản phẩm PENDING được phép có selling_status = 'ACTIVE'.
ALTER TABLE products DROP CHECK chk_products_active_approved;

-- Các sản phẩm đã gửi duyệt từ trước (PENDING + DRAFT) nay được xuất bản.
UPDATE products
SET selling_status = 'ACTIVE'
WHERE approval_status = 'PENDING' AND selling_status = 'DRAFT';

-- Biến thể DRAFT thuộc các sản phẩm PENDING đã được đưa lên sàn cũng được kích hoạt,
-- để chúng có thể xuất hiện trong danh mục khách hàng; không đụng tới sản phẩm DRAFT/REJECTED.
UPDATE product_variants pv
JOIN products p ON p.id = pv.product_id
SET pv.status = 'ACTIVE'
WHERE p.approval_status = 'PENDING'
  AND p.selling_status = 'ACTIVE'
  AND pv.status = 'DRAFT';
