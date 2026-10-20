# Duyệt biến thể sản phẩm

## Luồng mới

- `products` chỉ lưu hồ sơ nhóm/thông tin chung (tên, danh mục, xuất xứ, lô và hạn sử dụng). Không còn được gửi duyệt ở Operations.
- Shop Owner tạo và hoàn thiện từng biến thể (ảnh, SKU, quy cách, giá, đơn vị, tồn kho), sau đó chọn **Gửi duyệt**. Trạng thái chuyển sang `PENDING`; trong thời gian chờ, biến thể không được bán và không thể chỉnh sửa.
- Operations Manager mở **Duyệt biến thể sản phẩm**, xem ảnh và đầy đủ thông tin biến thể cùng tồn kho, lô hàng và thông tin shop, rồi duyệt hoặc từ chối (bắt buộc nhập lý do).
- Chỉ biến thể `approval_status = 'APPROVED'` và `status = 'ACTIVE'` mới đủ điều kiện được hiển thị/mua trên sàn. Biến thể bị từ chối bị ngừng bán; Shop Owner sửa rồi gửi duyệt lại.
- Danh mục, giỏ hàng, trang chi tiết, yêu thích, khuyến mãi và checkout đã được cập nhật để không cho bán biến thể chưa duyệt.

## Database

### Database hiện có (giữ dữ liệu)

1. Sao lưu database trước.
2. Chạy **một lần** file `src/main/resources/database` trên đúng database FreshFruit. File này bổ sung trạng thái duyệt vào `product_variants`, chuyển trạng thái cũ sang từng biến thể, gỡ ràng buộc duyệt/bán ở cấp sản phẩm cha và giữ nguyên các bảng/dữ liệu khác.
3. Không chạy lại migration sau khi đã áp dụng; file migration không được thiết kế để chạy lặp.

### Tạo database mới

Dùng `OnlineFruitShoppingPlatform.sql` — schema đầy đủ đã có các cột duyệt biến thể. Không chạy migration trên database mới đã tạo từ schema này.

## Các trang chính

- Shop Owner — danh sách biến thể: `/shopowner/product-variants`
- Operations — hàng đợi duyệt: `/operations/variants`
- Operations — chi tiết biến thể: `/operations/variants/{id}`

## Kiểm thử

Đã rà soát cú pháp JavaScript cho các script duyệt biến thể và cấu trúc ZIP. Không thể chạy Maven compile/JUnit tại môi trường đóng gói vì Maven Wrapper không tải được distribution từ Maven Central; hãy chạy `./mvnw test` (hoặc `mvnw.cmd test` trên Windows) trước khi merge.
