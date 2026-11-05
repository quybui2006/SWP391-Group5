
-- ============================================================
-- TẠO CÁC BẢNG QUẢN LÝ TỒN KHO THEO LÔ (BATCH)
-- Không chỉnh sửa các migration đã được chạy trước đó.
-- Script này cần được đặt trong một migration Flyway mới.
-- ============================================================


-- ============================================================
-- 1. BẢNG inventory_batches
-- Lưu thông tin từng lô hàng nhập kho của một biến thể sản phẩm.
-- Mỗi biến thể có thể có nhiều lô hàng khác nhau.
-- ============================================================

CREATE TABLE inventory_batches (
    -- ID tự tăng, định danh duy nhất của mỗi lô hàng.
                                   id BIGINT NOT NULL AUTO_INCREMENT,

    -- ID liên kết đến bản ghi tồn kho của biến thể trong variant_inventory.
                                   inventory_id BIGINT NOT NULL,

    -- Mã lô hàng, có thể để NULL.
    -- Mỗi mã lô hàng phải là duy nhất trong toàn bảng.
                                   batch_code VARCHAR(40) NULL,

    -- Tổng số lượng hàng được nhập vào lô ban đầu.
                                   received_quantity INT NOT NULL,

    -- Số lượng hàng thực tế hiện còn trong lô.
                                   quantity_on_hand INT NOT NULL,

    -- Số lượng hàng đang được giữ cho các đơn hàng.
    -- Mặc định bằng 0 khi tạo lô mới.
                                   reserved_quantity INT NOT NULL DEFAULT 0,

    -- Số lượng hàng khả dụng, được MySQL tự động tính:
    -- Số lượng thực tế - Số lượng đang được giữ.
    -- STORED nghĩa là kết quả được lưu trong Database.
                                   available_quantity INT GENERATED ALWAYS AS (quantity_on_hand - reserved_quantity) STORED,

    -- Thời điểm lô hàng được nhập vào kho.
                                   received_at DATETIME(3) NOT NULL,

    -- Trạng thái lô hàng.
    -- ACTIVE: Lô hàng đang hoạt động.
    -- CLOSED: Lô hàng đã đóng.
                                   status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    -- Ghi chú bổ sung về lô hàng.
                                   note VARCHAR(1000) NULL,

    -- ID người dùng thực hiện tạo lô hàng.
    -- Liên kết đến bảng users.
                                   created_by BIGINT NULL,

    -- Thời điểm tạo bản ghi, mặc định là thời gian hiện tại.
                                   created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

                                   PRIMARY KEY (id),

    -- Không cho phép trùng mã lô hàng.
    -- MySQL vẫn cho phép nhiều giá trị NULL trong UNIQUE KEY.
                                   UNIQUE KEY UX_inventory_batches_code (batch_code),

    -- Khóa ngoại liên kết lô hàng với bản ghi tồn kho của biến thể.
                                   CONSTRAINT FK_inventory_batches_inventory
                                       FOREIGN KEY (inventory_id) REFERENCES variant_inventory(id),

    -- Khóa ngoại liên kết người tạo lô hàng với bảng users.
                                   CONSTRAINT FK_inventory_batches_creator
                                       FOREIGN KEY (created_by) REFERENCES users(id),

    -- Số lượng nhập ban đầu phải lớn hơn hoặc bằng 0.
                                   CONSTRAINT CK_inventory_batches_received
                                       CHECK (received_quantity >= 0),

    -- Số lượng tồn thực tế không được âm.
                                   CONSTRAINT CK_inventory_batches_on_hand
                                       CHECK (quantity_on_hand >= 0),

    -- Số lượng đang giữ không được âm và không được lớn hơn tồn thực tế.
                                   CONSTRAINT CK_inventory_batches_reserved
                                       CHECK (reserved_quantity >= 0 AND reserved_quantity <= quantity_on_hand),

    -- Chỉ chấp nhận hai trạng thái ACTIVE và CLOSED.
                                   CONSTRAINT CK_inventory_batches_status
                                       CHECK (status IN ('ACTIVE', 'CLOSED'))

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 2. BẢNG order_item_batch_allocations
-- Theo dõi việc phân bổ số lượng hàng từ từng lô cho từng sản phẩm
-- trong đơn hàng.
--
-- Một order_item có thể được phân bổ từ nhiều batch khác nhau.
-- Mỗi cặp order_item_id và batch_id chỉ được xuất hiện một lần.
-- ============================================================

CREATE TABLE order_item_batch_allocations (
    -- ID tự tăng của bản ghi phân bổ.
                                              id BIGINT NOT NULL AUTO_INCREMENT,

    -- ID chi tiết sản phẩm trong đơn hàng.
    -- Liên kết đến bảng order_items.
                                              order_item_id BIGINT NOT NULL,

    -- ID lô hàng được sử dụng để cung cấp sản phẩm cho đơn hàng.
    -- Liên kết đến bảng inventory_batches.
                                              batch_id BIGINT NOT NULL,

    -- Số lượng hàng được phân bổ từ lô này cho đơn hàng.
                                              quantity INT NOT NULL,

    -- Trạng thái phân bổ:
    -- RESERVED: Số lượng hàng đang được giữ.
    -- CONSUMED: Số lượng hàng đã được xuất bán.
    -- RELEASED: Số lượng hàng đã được giải phóng.
                                              status VARCHAR(20) NOT NULL,

    -- Thời điểm tạo bản ghi phân bổ.
                                              created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    -- Thời điểm cập nhật bản ghi gần nhất.
    -- Tự động thay đổi khi bản ghi được UPDATE.
                                              updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

                                              PRIMARY KEY (id),

    -- Không cho phép một chi tiết đơn hàng phân bổ hai bản ghi
    -- cho cùng một lô hàng.
                                              UNIQUE KEY UX_order_item_batch (order_item_id, batch_id),

    -- Tạo chỉ mục hỗ trợ tìm kiếm các bản ghi phân bổ
    -- theo batch_id và trạng thái.
                                              KEY IX_batch_allocations_status (batch_id, status),

    -- Khóa ngoại liên kết với chi tiết đơn hàng.
                                              CONSTRAINT FK_batch_allocations_item
                                                  FOREIGN KEY (order_item_id) REFERENCES order_items(id),

    -- Khóa ngoại liên kết với lô hàng.
                                              CONSTRAINT FK_batch_allocations_batch
                                                  FOREIGN KEY (batch_id) REFERENCES inventory_batches(id),

    -- Số lượng phân bổ phải lớn hơn 0.
                                              CONSTRAINT CK_batch_allocations_quantity
                                                  CHECK (quantity > 0),

    -- Chỉ chấp nhận các trạng thái phân bổ hợp lệ.
                                              CONSTRAINT CK_batch_allocations_status
                                                  CHECK (status IN ('RESERVED', 'CONSUMED', 'RELEASED'))

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 3. BẢNG inventory_batch_transactions
-- Lưu lịch sử tất cả biến động của từng lô hàng.
--
-- Mỗi lần nhập kho, điều chỉnh, giữ hàng, giải phóng hàng,
-- xuất bán hoặc loại bỏ hàng đều được ghi nhận tại đây.
--
-- Bảng này giúp theo dõi số lượng thay đổi và lý do thay đổi.
-- ============================================================

CREATE TABLE inventory_batch_transactions (
    -- ID tự tăng của giao dịch kho.
                                              id BIGINT NOT NULL AUTO_INCREMENT,

    -- ID lô hàng phát sinh giao dịch.
                                              batch_id BIGINT NOT NULL,

    -- ID chi tiết đơn hàng liên quan.
    -- Có thể NULL nếu giao dịch không liên quan đến đơn hàng.
                                              order_item_id BIGINT NULL,

    -- Loại giao dịch:
    -- OPENING: Ghi nhận tồn kho ban đầu.
    -- RECEIVE: Nhập thêm hàng.
    -- ADJUST: Điều chỉnh giảm số lượng.
    -- RESERVE: Giữ hàng cho đơn hàng.
    -- RELEASE: Giải phóng hàng đã giữ.
    -- SALE: Xuất bán hàng.
    -- DISCARD: Loại bỏ hàng khỏi kho.
                                              type VARCHAR(20) NOT NULL,

    -- Số lượng thực tế thay đổi:
    -- Số dương: Tăng tồn kho.
    -- Số âm: Giảm tồn kho.
    -- 0: Không thay đổi tồn thực tế.
                                              on_hand_delta INT NOT NULL,

    -- Số lượng hàng đang giữ thay đổi:
    -- Số dương: Tăng số lượng giữ.
    -- Số âm: Giảm số lượng giữ.
    -- 0: Không thay đổi số lượng giữ.
                                              reserved_delta INT NOT NULL,

    -- Số lượng tồn thực tế sau khi hoàn thành giao dịch.
                                              on_hand_after INT NOT NULL,

    -- Số lượng hàng đang giữ sau khi hoàn thành giao dịch.
                                              reserved_after INT NOT NULL,

    -- Mã sự kiện duy nhất để tránh ghi nhận trùng một giao dịch.
                                              event_key VARCHAR(150) NOT NULL,

    -- Lý do thực hiện giao dịch.
                                              reason VARCHAR(1000) NULL,

    -- ID người dùng thực hiện giao dịch.
                                              created_by BIGINT NULL,

    -- Thời điểm ghi nhận giao dịch.
                                              created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

                                              PRIMARY KEY (id),

    -- Mỗi event_key chỉ được sử dụng một lần trong toàn bảng.
    -- Hỗ trợ ngăn việc ghi nhận trùng một sự kiện.
                                              UNIQUE KEY UX_inventory_batch_event (event_key),

    -- Chỉ mục hỗ trợ truy vấn lịch sử giao dịch theo lô hàng
    -- và sắp xếp theo thời gian mới nhất.
                                              KEY IX_inventory_batch_history (batch_id, created_at DESC),

    -- Khóa ngoại liên kết giao dịch với lô hàng.
                                              CONSTRAINT FK_inventory_batch_tx_batch
                                                  FOREIGN KEY (batch_id) REFERENCES inventory_batches(id),

    -- Khóa ngoại liên kết giao dịch với chi tiết đơn hàng.
                                              CONSTRAINT FK_inventory_batch_tx_item
                                                  FOREIGN KEY (order_item_id) REFERENCES order_items(id),

    -- Khóa ngoại liên kết người thực hiện giao dịch với bảng users.
                                              CONSTRAINT FK_inventory_batch_tx_user
                                                  FOREIGN KEY (created_by) REFERENCES users(id),

    -- Chỉ chấp nhận các loại giao dịch được quy định.
                                              CONSTRAINT CK_inventory_batch_tx_type
                                                  CHECK (type IN ('OPENING', 'RECEIVE', 'ADJUST', 'RESERVE', 'RELEASE', 'SALE', 'DISCARD')),

    -- Số lượng tồn thực tế sau giao dịch không được âm.
                                              CONSTRAINT CK_inventory_batch_tx_on_hand
                                                  CHECK (on_hand_after >= 0),

    -- Số lượng giữ không được âm và không được lớn hơn tồn thực tế.
                                              CONSTRAINT CK_inventory_batch_tx_reserved
                                                  CHECK (reserved_after >= 0 AND reserved_after <= on_hand_after),

    -- Kiểm tra tính hợp lệ của số lượng thay đổi theo từng loại giao dịch.
                                              CONSTRAINT CK_inventory_batch_tx_delta CHECK (

                                                  -- OPENING: Ghi nhận tồn kho ban đầu.
                                                  -- Tồn thực tế và số lượng giữ không được giảm.
                                                  (type = 'OPENING'
                                                      AND on_hand_delta >= 0
                                                      AND reserved_delta >= 0)

                                                      OR

                                                      -- RECEIVE: Nhập thêm hàng.
                                                      -- Tồn thực tế phải tăng, số lượng giữ không thay đổi.
                                                  (type = 'RECEIVE'
                                                      AND on_hand_delta > 0
                                                      AND reserved_delta = 0)

                                                      OR

                                                      -- ADJUST: Điều chỉnh giảm tồn kho.
                                                      -- Tồn thực tế giảm, số lượng giữ không thay đổi.
                                                      -- Bắt buộc phải có lý do điều chỉnh.
                                                  (type = 'ADJUST'
                                                      AND on_hand_delta < 0
                                                      AND reserved_delta = 0
                                                      AND reason IS NOT NULL
                                                      AND CHAR_LENGTH(TRIM(reason)) > 0)

                                                      OR

                                                      -- RESERVE: Giữ hàng cho đơn hàng.
                                                      -- Tồn thực tế không đổi, số lượng giữ tăng.
                                                      -- Bắt buộc phải có order_item_id.
                                                  (type = 'RESERVE'
                                                      AND on_hand_delta = 0
                                                      AND reserved_delta > 0
                                                      AND order_item_id IS NOT NULL)

                                                      OR

                                                      -- RELEASE: Giải phóng hàng đã giữ.
                                                      -- Tồn thực tế không đổi, số lượng giữ giảm.
                                                      -- Bắt buộc phải có order_item_id.
                                                  (type = 'RELEASE'
                                                      AND on_hand_delta = 0
                                                      AND reserved_delta < 0
                                                      AND order_item_id IS NOT NULL)

                                                      OR

                                                      -- SALE: Xuất hàng khi bán.
                                                      -- Tồn thực tế phải giảm.
                                                      -- Số lượng giữ có thể không đổi hoặc giảm bằng đúng số lượng xuất.
                                                      -- Bắt buộc phải có order_item_id.
                                                  (type = 'SALE'
                                                      AND on_hand_delta < 0
                                                      AND reserved_delta IN (0, on_hand_delta)
                                                      AND order_item_id IS NOT NULL)

                                                      OR

                                                      -- DISCARD: Loại bỏ hàng khỏi kho.
                                                      -- Tồn thực tế giảm, số lượng giữ không thay đổi.
                                                      -- Bắt buộc phải có lý do loại bỏ.
                                                  (type = 'DISCARD'
                                                      AND on_hand_delta < 0
                                                      AND reserved_delta = 0
                                                      AND reason IS NOT NULL
                                                      AND CHAR_LENGTH(TRIM(reason)) > 0)
                                                  )

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 4. CHUYỂN DỮ LIỆU TỒN KHO CŨ SANG QUẢN LÝ THEO LÔ
--
-- Mỗi bản ghi tồn kho hiện tại trong variant_inventory
-- sẽ được chuyển thành một lô hàng ban đầu.
--
-- Mục đích:
-- Không làm mất số lượng tồn kho hiện có khi triển khai
-- chức năng quản lý tồn kho theo batch.
-- ============================================================

INSERT INTO inventory_batches (
    inventory_id,
    batch_code,
    received_quantity,
    quantity_on_hand,
    reserved_quantity,
    received_at,
    status,
    note,
    created_by,
    created_at
)

-- Lấy dữ liệu tồn kho hiện tại từ bảng variant_inventory.
SELECT
    vi.id,

    -- Tạo mã lô cũ theo cấu trúc LEGACY- + ID có 8 chữ số.
    CONCAT('LEGACY-', LPAD(vi.id, 8, '0')),

    -- Số lượng nhập ban đầu bằng số lượng tồn hiện tại.
    vi.quantity_on_hand,

    -- Giữ nguyên số lượng tồn thực tế hiện tại.
    vi.quantity_on_hand,

    -- Giữ nguyên số lượng đang được giữ của biến thể.
    vi.reserved_quantity,

    -- Sử dụng thời điểm tạo bản ghi tồn kho cũ làm ngày nhập.
    vi.created_at,

    -- Nếu tồn kho bằng 0 thì đóng lô.
    -- Nếu tồn kho lớn hơn 0 thì lô vẫn hoạt động.
    IF(vi.quantity_on_hand = 0, 'CLOSED', 'ACTIVE'),

    -- Ghi chú cho biết đây là lô chuyển đổi từ dữ liệu cũ.
    'Tồn kho chuyển đổi từ dữ liệu cũ',

    -- Giữ lại người tạo của bản ghi tồn kho cũ.
    vi.created_by,

    -- Giữ nguyên thời điểm tạo ban đầu.
    vi.created_at

FROM variant_inventory vi;


-- ============================================================
-- 5. GHI NHẬN GIAO DỊCH OPENING CHO CÁC LÔ ĐÃ CHUYỂN ĐỔI
--
-- Sau khi tạo các batch từ dữ liệu cũ, ghi thêm một giao dịch
-- OPENING tương ứng để lưu lại trạng thái tồn kho ban đầu.
-- ============================================================

INSERT INTO inventory_batch_transactions (
    batch_id,
    type,
    on_hand_delta,
    reserved_delta,
    on_hand_after,
    reserved_after,
    event_key,
    reason,
    created_by,
    created_at
)

SELECT
    -- ID của lô hàng vừa được tạo.
    b.id,

    -- Loại giao dịch là khởi tạo tồn kho.
    'OPENING',

    -- Số lượng tồn thực tế ban đầu.
    b.quantity_on_hand,

    -- Số lượng đã được giữ tại thời điểm chuyển đổi.
    b.reserved_quantity,

    -- Tồn thực tế sau khi khởi tạo.
    b.quantity_on_hand,

    -- Số lượng giữ sau khi khởi tạo.
    b.reserved_quantity,

    -- Tạo mã sự kiện duy nhất cho giao dịch khởi tạo.
    CONCAT('opening-', b.id),

    -- Giải thích nguồn gốc giao dịch.
    'Chuyển tồn kho hiện có sang quản lý theo lô',

    -- Giữ lại người tạo của lô hàng.
    b.created_by,

    -- Giữ lại thời điểm tạo lô hàng.
    b.created_at

FROM inventory_batches b;


-- ============================================================
-- 6. CHUYỂN CÁC ĐƠN HÀNG ĐANG GIỮ HÀNG SANG QUẢN LÝ THEO LÔ
--
-- Trước khi triển khai quản lý batch, hệ thống có thể đã giữ
-- hàng ở cấp variant_inventory.
--
-- Cần liên kết các khoản giữ hàng hiện có với lô LEGACY
-- tương ứng để không mất thông tin phân bổ khi chuyển đổi.
-- ============================================================

INSERT INTO order_item_batch_allocations (
    order_item_id,
    batch_id,
    quantity,
    status
)

SELECT
    -- ID chi tiết đơn hàng đang giữ hàng.
    oi.id,

    -- ID lô hàng khởi tạo tương ứng với tồn kho của biến thể.
    b.id,

    -- Số lượng đang được giữ theo chi tiết đơn hàng.
    oi.quantity,

    -- Giữ nguyên trạng thái đang giữ hàng.
    'RESERVED'

FROM order_items oi

-- Liên kết đơn hàng với lô hàng thông qua inventory_id.
         JOIN inventory_batches b
              ON b.inventory_id = oi.inventory_id

-- Chỉ chuyển những chi tiết đơn hàng đang có trạng thái RESERVED.
WHERE oi.allocation_status = 'RESERVED';


-- ============================================================
-- 7. GHI LỊCH SỬ RESERVE CHO CÁC ĐƠN HÀNG ĐANG GIỮ HÀNG
--
-- Sau khi tạo các bản ghi phân bổ batch, bổ sung giao dịch
-- RESERVE tương ứng vào bảng inventory_batch_transactions.
--
-- Mục đích:
-- Đảm bảo lịch sử các lô hàng phản ánh những đơn hàng
-- đã được giữ hàng trước khi chuyển đổi.
-- ============================================================

INSERT INTO inventory_batch_transactions (
    batch_id,
    order_item_id,
    type,
    on_hand_delta,
    reserved_delta,
    on_hand_after,
    reserved_after,
    event_key,
    reason,
    created_at
)

SELECT
    -- ID của lô hàng đang được giữ.
    b.id,

    -- ID chi tiết đơn hàng thực hiện giữ hàng.
    oi.id,

    -- Loại giao dịch là giữ hàng.
    'RESERVE',

    -- Giữ hàng không làm thay đổi số lượng tồn thực tế.
    0,

    -- Số lượng giữ tăng theo số lượng của chi tiết đơn hàng.
    oi.quantity,

    -- Giữ nguyên số lượng tồn thực tế của lô.
    b.quantity_on_hand,

    -- Sử dụng số lượng giữ hiện tại của lô.
    b.reserved_quantity,

    -- Tạo mã sự kiện duy nhất cho lần giữ hàng chuyển đổi.
    CONCAT('legacy-reserve-', oi.id),

    -- Ghi rõ đơn hàng nào đang giữ lô hàng.
    CONCAT('Đang giữ cho đơn ', o.order_code),

    -- Ghi nhận thời điểm thực hiện chuyển đổi.
    CURRENT_TIMESTAMP(3)

FROM order_items oi

-- Lấy thông tin đơn hàng để truy xuất mã đơn hàng.
         JOIN orders o
              ON o.id = oi.order_id

-- Liên kết chi tiết đơn hàng với lô hàng tương ứng.
         JOIN inventory_batches b
              ON b.inventory_id = oi.inventory_id

-- Chỉ ghi nhận các chi tiết đơn hàng vẫn đang được giữ hàng.
WHERE oi.allocation_status = 'RESERVED';
