-- Migration V38: Xóa hoàn toàn hệ thống điểm uy tín Shop (Shop Reputation) khỏi CSDL

-- 1. Xóa các thông báo lịch sử liên quan đến cảnh báo điểm uy tín Shop giảm sâu
DELETE FROM notifications WHERE event_key LIKE 'SHOP_LOW_REPUTATION_%';

-- 2. Xóa bảng lịch sử điểm uy tín Shop
DROP TABLE IF EXISTS shop_reputation_logs;

-- 3. Xóa cột reputation_score khỏi bảng shops nếu còn tồn tại
SET @has_reputation = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'shops'
      AND COLUMN_NAME = 'reputation_score'
);
SET @drop_reputation_sql = IF(@has_reputation > 0,
    'ALTER TABLE shops DROP COLUMN reputation_score',
    'SELECT 1');
PREPARE drop_reputation_stmt FROM @drop_reputation_sql;
EXECUTE drop_reputation_stmt;
DEALLOCATE PREPARE drop_reputation_stmt;
