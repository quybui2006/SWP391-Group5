## Quy tắc làm việc nhóm

### 1. Nhánh
- main: phiên bản ổn định để demo hoặc nộp bài.
- staging: kiểm thử tổng thể.
- develop: tích hợp code của các thành viên.
- Mỗi nhiệm vụ tạo một nhánh riêng từ develop mới nhất.
- Không push trực tiếp lên main, staging hoặc develop.
  
LUỒNG CODE:
FEAT/* HOẶC FIX/* → DEVELOP → STAGING → MAIN.

### 2. Đặt tên nhánh
- feat/<ten-chuc-nang>: chức năng mới.
- fix/<ten-loi>: sửa lỗi.
- docs/<noi-dung>: cập nhật tài liệu.
- chore/<noi-dung>: cấu hình dự án.

Ví dụ: feat/shop-registration, feat/delivery-status.

### 3. Commit
- Mỗi commit tập trung vào một thay đổi rõ ràng.
- Format: type(name): short description
- Dùng feat, fix, docs, chore, refactor hoặc test.
- Ví dụ: feat(login): tạo login
- Không đặt tên chung chung như "update", "done", "fix".

### 4. Pull Request và review
- PR chức năng gửi vào develop.
- PR đưa lên staging lấy từ develop.
- PR đưa lên main lấy từ staging.
- Ghi rõ nội dung thay đổi và kết quả.
- Vào Pull Request => Create Pull Request => Chọn Base là nhánh cần merge vào, Compare là nhánh để merge
- Chỉ xóa nhánh nhiệm vụ sau khi PR merged.
- Giữ nguyên main, staging và develop.
