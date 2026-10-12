## Quy tắc 
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

## Chạy ứng dụng

Yêu cầu JDK 17 và MySQL. Trong MySQL Workbench, chạy file `OnlineFruitShoppingPlatform.sql` để tạo database `freshfruit_v5` và các bảng.

Mở PowerShell tại thư mục dự án, cấu hình thông tin MySQL rồi chạy:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "mat-khau-mysql"
.\mvnw.cmd spring-boot:run
```

Ứng dụng chạy tại `http://localhost:8080`. Chạy test bằng `.\mvnw.cmd test`; bộ test sử dụng H2 riêng và không cần MySQL.

## 📧 Hướng dẫn Cấu hình Gửi Email OTP (Mailtrap)


Trong file `src/main/resources/application.properties`, hệ thống sử dụng 2 biến môi trường:
spring.mail.username=${MAIL_USERNAME}
spring.mail.password=${MAIL_PASSWORD}
Mỗi thành viên trong nhóm cần thiết lập thông tin Mailtrap trên máy cá nhân theo cách sau:
Bước 1: Lấy tài khoản Mailtrap cá nhân (Miễn phí)
1.
Truy cập https://mailtrap.io và đăng ký/đăng nhập (bằng tài khoản Google/GitHub).
2.
Chọn menu Sandboxes -> Chọn My sandbox (hoặc tạo inbox mới).
3.
Sao chép 2 giá trị:
◦
username (ví dụ: 22030d98418ab6)
◦
password (ví dụ: 8a792389d582c6)

Bước 2: Cấu hình trên máy phát triển 
*Thiết lập trực tiếp trên IntelliJ IDEA (Khuyên dùng - Không lo commit nhầm mật khẩu)
1.
Trong IntelliJ IDEA, mở menu chọn cấu hình chạy: Run -> Edit Configurations...
2.
Chọn cấu hình chạy của dự án: Spring Boot -> Swp391Group5Application.
3.
Tìm đến ô Environment variables (Biến môi trường), bấm vào biểu tượng chỉnh sửa và thêm 2 biến:
◦
MAIL_USERNAME = (username lấy từ Mailtrap)
◦
MAIL_PASSWORD = (password lấy từ Mailtrap)
4.
Bấm Apply -> OK rồi chạy lại dự án (Run/Debug).
💡 Cách này an toàn nhất vì không chỉnh sửa file mã nguồn, tránh nguy cơ vô tình push lộ credential cá nhân lên GitHub.
