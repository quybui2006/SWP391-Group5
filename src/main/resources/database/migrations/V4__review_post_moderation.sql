-- V4: Hậu kiểm review mới
-- Review đã tồn tại từ trước V4 giữ trạng thái APPROVED.
-- Review tạo mới sẽ chờ CS kiểm tra.

UPDATE reviews
SET moderation_status = 'REVIEWED',
    moderated_at = COALESCE(moderated_at, CURRENT_TIMESTAMP(3))
WHERE moderation_status = 'APPROVED';

ALTER TABLE reviews
    ALTER COLUMN moderation_status SET DEFAULT 'PENDING';
