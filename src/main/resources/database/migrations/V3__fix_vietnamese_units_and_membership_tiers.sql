-- Cập nhật tên đơn vị tính
UPDATE units SET name = 'Hộp' WHERE code = 'BOX';
UPDATE units SET name = 'Thùng' WHERE code = 'CARTON';
UPDATE units SET name = 'Quả' WHERE code = 'PIECE';
UPDATE units SET name = 'Gói' WHERE code = 'PACK';

-- Cập nhật tên hạng thành viên
UPDATE membership_tiers SET name = 'Đồng' WHERE code = 'BRONZE';
UPDATE membership_tiers SET name = 'Bạc' WHERE code = 'SILVER';
UPDATE membership_tiers SET name = 'Vàng' WHERE code = 'GOLD';
UPDATE membership_tiers SET name = 'Kim cương' WHERE code = 'DIAMOND';