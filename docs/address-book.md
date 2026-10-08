# Address Book

URL: `/customer/account/address`.

## Luồng xử lý

GET → CustomerAccountController → AddressBookService → MySQL → Thymeleaf.
Form POST → kiểm tra token session, customer và dữ liệu → transaction → redirect GET.
JavaScript chỉ mở modal, điền dữ liệu sửa, kiểm tra điện thoại và lọc tỉnh/huyện/xã.

## Các file

- `entity/UserAddresses.java`: hoàn thiện entity ban đầu, ánh xạ bảng user_addresses.
- `entity/User.java`, `entity/Province.java`: thông tin customer và tỉnh từ database.
- `repository/UserAddressesRepository.java`: truy vấn theo ID địa chỉ VÀ user ID.
- `repository/UserRepository.java`, `repository/ProvinceRepository.java`: truy cập user/tỉnh.
- `dto/AddressForm.java`: dữ liệu form; không nhận user ID từ form lưu địa chỉ.
- `service/AddressBookService.java`: CRUD, validation server, transaction và khóa dòng user để tuần tự hóa việc đổi mặc định.
- `service/AddressLocationService.java`: đọc danh mục địa phương và kiểm tra tỉnh/huyện/xã khớp nhau.
- `controller/CustomerAccountController.java`: GET trang, POST save/default/delete/customer, flash thông báo và giữ form khi lỗi.
- `templates/customer/address-book.html`: render dữ liệu thật bằng th:each/th:text, form POST.
- `static/js/customer-account.js`: tương tác form; đã bỏ mảng địa chỉ giả và lưu tạm.
- `static/css/address-book-forms.css`: bổ sung style form/thông báo.
- `static/data/vietnam-addresses-v1.json`: snapshot danh mục đầy đủ 3 cấp từ https://provinces.open-api.vn/api/v1/?depth=3, tải 08/10/2026. Đây là danh mục trước sắp xếp 2025, không phải danh mục hiện hành 2 cấp. Được phục vụ local, không phụ thuộc API ngoài lúc sử dụng.
- `AddressBookIntegrationTests.java`: kiểm tra với MySQL, rollback dữ liệu test.

## Quy tắc

- Customer được lấy qua users → user_roles → roles với code CUSTOMER, status ACTIVE.
- Chưa có đăng nhập: bộ chọn customer trên UI dùng session, chỉ dành cho chạy thử; không phải cơ chế xác thực. Khi tích hợp đăng nhập, bỏ bộ chọn và lấy ID từ principal/session đăng nhập.
- Nếu chưa có customer, tạo `customer.demo@freshfruit.example` và gán role CUSTOMER; không tạo mật khẩu đăng nhập giả.
- Địa chỉ đầu tiên mặc định. Thêm/sửa có chọn mặc định thì bỏ mặc định các địa chỉ còn lại.
- Xóa địa chỉ mặc định: chọn một địa chỉ còn lại làm mặc định; xóa hết thì hiển thị trạng thái rỗng.
- Điện thoại: chuẩn hóa khoảng trắng/dấu chấm/gạch nối và +84 thành 0, kiểm tra 10 số đầu 03/05/07/08/09 ở cả client và server. Đây là kiểm tra định dạng, không xác minh thuê bao tồn tại.
- Tên tối đa 150 ký tự, địa chỉ chi tiết tối đa 500; bắt buộc tỉnh/huyện/xã hợp lệ.
- Schema gốc không có district_name: lưu `ward_name` dưới dạng `Tên xã, Tên huyện` (tối đa 150 ký tự). Không đổi schema tự động.
- Province được tìm theo tên có sẵn; nếu chưa có thì thêm với code `legacy-<mã tỉnh>` để giữ FK và phân biệt danh mục cũ.

## Chạy và kiểm tra

1. Database freshfruit_v5 phải có schema từ SQL của project.
2. Chạy `./mvnw.cmd test`.
3. Restart ứng dụng để nhận controller mới. Mở route, chọn customer.
4. Thêm địa chỉ, refresh để kiểm tra lưu thật; sửa, đổi mặc định, xóa và chuyển customer để kiểm tra phân tách dữ liệu.
5. Có thể kiểm tra MySQL: `SELECT * FROM user_addresses ORDER BY user_id, is_default DESC;`.
