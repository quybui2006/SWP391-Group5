package service.shop;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InventoryBatchService {
    private final JdbcTemplate jdbc;

    public InventoryBatchService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Long receive(Long inventoryId, int quantity, LocalDateTime receivedAt, String note, Long ownerId) {
        if (quantity <= 0) throw new InventoryActionException("Số lượng nhập phải lớn hơn 0.");
        if (receivedAt == null || receivedAt.isAfter(LocalDateTime.now()))
            throw new InventoryActionException("Thời điểm nhập không được để trống hoặc ở tương lai.");
        if (note != null && note.length() > 1000) throw new InventoryActionException("Ghi chú không được vượt quá 1000 ký tự.");

        lockInventory(inventoryId);
        if (ownerId == null || jdbc.queryForObject("SELECT COUNT(*) FROM shops s JOIN product_variants pv ON pv.shop_id=s.id JOIN variant_inventory vi ON vi.variant_id=pv.id WHERE vi.id=? AND s.owner_id=?", Integer.class, inventoryId, ownerId) != 1)
            throw new InventoryActionException("Bạn không có quyền nhập tồn kho cho biến thể này.");
        int currentOnHand = jdbc.queryForObject("SELECT quantity_on_hand FROM variant_inventory WHERE id=?", Integer.class, inventoryId);
        checkedAdd(currentOnHand, quantity, "Tổng tồn kho vượt giới hạn cho phép.");

        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO inventory_batches(inventory_id,batch_code,received_quantity,quantity_on_hand,reserved_quantity,received_at,status,note,created_by) VALUES(?,NULL,?,?,0,?,'ACTIVE',?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, inventoryId);
            statement.setInt(2, quantity);
            statement.setInt(3, quantity);
            statement.setTimestamp(4, java.sql.Timestamp.valueOf(receivedAt));
            statement.setString(5, clean(note));
            statement.setLong(6, ownerId);
            return statement;
        }, key);
        if (key.getKey() == null) throw new InventoryActionException("Không tạo được mã batch.");
        long batchId = key.getKey().longValue();
        String batchCode = "LOT-" + String.format(java.util.Locale.ROOT, "%08d", batchId);
        jdbc.update("UPDATE inventory_batches SET batch_code=? WHERE id=?", batchCode, batchId);
        if (jdbc.update("UPDATE variant_inventory SET quantity_on_hand=quantity_on_hand+? WHERE id=?", quantity, inventoryId) != 1)
            throw new InventoryActionException("Không cập nhật được tổng tồn kho.");
        int onHand = jdbc.queryForObject("SELECT quantity_on_hand FROM variant_inventory WHERE id=?", Integer.class, inventoryId);
        int reserved = jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE id=?", Integer.class, inventoryId);
        String event = "batch-receive-" + UUID.randomUUID();
        jdbc.update("INSERT INTO inventory_transactions(inventory_id,type,on_hand_delta,reserved_delta,on_hand_after,reserved_after,event_key,reason,created_by) VALUES(?,'RECEIVE',?,0,?,?,?, ?,?)",
                inventoryId, quantity, onHand, reserved, event, "Nhập batch " + batchCode, ownerId);
        batchTransaction(batchId, null, "RECEIVE", quantity, 0, quantity, 0, "receive-" + UUID.randomUUID(), clean(note), ownerId);
        return batchId;
    }

    @Transactional
    public void adjust(Long batchId, String direction, int quantity, String reason, Long ownerId) {
        if (batchId == null) throw new InventoryActionException("Vui lòng chọn batch cần điều chỉnh.");
        if (quantity <= 0) throw new InventoryActionException("Số lượng điều chỉnh phải lớn hơn 0.");
        String cleanReason = clean(reason);
        if (cleanReason == null) throw new InventoryActionException("Vui lòng nhập lý do điều chỉnh.");
        if (cleanReason.length() > 1000) throw new InventoryActionException("Lý do không được vượt quá 1000 ký tự.");
        if (!"DECREASE".equals(direction))
            throw new InventoryActionException("Điều chỉnh chỉ được giảm tồn; muốn tăng số lượng, hãy tạo lô hàng mới.");

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT b.*,vi.quantity_on_hand AS aggregate_on_hand,vi.reserved_quantity AS aggregate_reserved,
                       vi.id AS inventory_id,s.owner_id
                FROM inventory_batches b
                JOIN variant_inventory vi ON vi.id=b.inventory_id
                JOIN product_variants pv ON pv.id=vi.variant_id
                JOIN shops s ON s.id=pv.shop_id
                WHERE b.id=? FOR UPDATE
                """, batchId);
        if (rows.isEmpty()) throw new InventoryActionException("Batch không tồn tại.");
        Map<String, Object> batch = rows.get(0);
        if (ownerId == null || ((Number) batch.get("owner_id")).longValue() != ownerId)
            throw new InventoryActionException("Bạn không có quyền điều chỉnh batch này.");
        long inventoryId = ((Number) batch.get("inventory_id")).longValue();
        int stock = ((Number) batch.get("quantity_on_hand")).intValue();
        int reserved = ((Number) batch.get("reserved_quantity")).intValue();
        int aggregateOnHand = ((Number) batch.get("aggregate_on_hand")).intValue();
        int aggregateReserved = ((Number) batch.get("aggregate_reserved")).intValue();
        int delta = -quantity;
        if (quantity > stock - reserved)
            throw new InventoryActionException("Không thể giảm quá số lượng chưa giữ của batch này (" + (stock - reserved) + ").");
        int after = checkedAdd(stock, delta, "Số lượng batch vượt giới hạn cho phép.");
        if (after < reserved) throw new InventoryActionException("Tồn sau điều chỉnh không thể thấp hơn số lượng đang giữ.");

        jdbc.update("UPDATE inventory_batches SET quantity_on_hand=?,status=? WHERE id=?",
                after, after == 0 ? "CLOSED" : "ACTIVE", batchId);
        jdbc.update("UPDATE variant_inventory SET quantity_on_hand=quantity_on_hand+? WHERE id=?", delta, inventoryId);
        int aggregateAfter = checkedAdd(aggregateOnHand, delta, "Tổng tồn kho vượt giới hạn cho phép.");
        String event = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO inventory_transactions(inventory_id,type,on_hand_delta,reserved_delta,on_hand_after,reserved_after,event_key,reason,created_by) VALUES(?,'ADJUST',?,0,?,?,?, ?,?)",
                inventoryId, delta, aggregateAfter, aggregateReserved, "batch-adjust-" + event, cleanReason, ownerId);
        batchTransaction(batchId, null, "ADJUST", delta, 0, after, reserved, "adjust-" + event, cleanReason, ownerId);
    }

    @Transactional
    public void reserveOrderItem(Long orderItemId, Long inventoryId, int quantity, Long createdBy) {
        if (quantity <= 0) throw new IllegalArgumentException("Số lượng cần giữ phải lớn hơn 0.");
        lockInventory(inventoryId);
        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT status,quantity FROM order_item_batch_allocations WHERE order_item_id=? FOR UPDATE", orderItemId);
        int reservedForItem = 0;
        for (Map<String, Object> allocation : existing) {
            String status = String.valueOf(allocation.get("status"));
            if ("CONSUMED".equals(status)) throw new IllegalStateException("Đơn này đã ghi nhận bán, không thể giữ tồn lần nữa.");
            if ("RESERVED".equals(status)) reservedForItem += ((Number) allocation.get("quantity")).intValue();
        }
        if (reservedForItem > 0) {
            if (reservedForItem == quantity) return;
            throw new IllegalStateException("Số lượng đang giữ không khớp với dòng hàng của đơn.");
        }
        // Reserve the oldest received stock first so all order holds follow FIFO.
        List<Map<String, Object>> batches = jdbc.queryForList("""
                SELECT id,quantity_on_hand,reserved_quantity
                FROM inventory_batches
                WHERE inventory_id=? AND status='ACTIVE'
                  AND quantity_on_hand>reserved_quantity
                ORDER BY received_at, id
                FOR UPDATE
                """, inventoryId);
        int remaining = quantity;
        for (Map<String, Object> batch : batches) {
            if (remaining == 0) break;
            long batchId = ((Number) batch.get("id")).longValue();
            int onHand = ((Number) batch.get("quantity_on_hand")).intValue();
            int reserved = ((Number) batch.get("reserved_quantity")).intValue();
            int take = Math.min(remaining, onHand - reserved);
            if (jdbc.update("UPDATE inventory_batches SET reserved_quantity=reserved_quantity+? WHERE id=? AND quantity_on_hand-reserved_quantity>=?", take, batchId, take) != 1)
                throw new IllegalArgumentException("Tồn kho vừa thay đổi. Vui lòng thử lại.");
            jdbc.update("INSERT INTO order_item_batch_allocations(order_item_id,batch_id,quantity,status) VALUES(?,?,?,'RESERVED') ON DUPLICATE KEY UPDATE quantity=IF(status='RELEASED',VALUES(quantity),quantity),status=IF(status='RELEASED','RESERVED',status)", orderItemId, batchId, take);
            String allocationStatus = jdbc.queryForObject("SELECT status FROM order_item_batch_allocations WHERE order_item_id=? AND batch_id=?", String.class, orderItemId, batchId);
            if (!"RESERVED".equals(allocationStatus)) throw new IllegalStateException("Không thể tạo lại phần hàng giữ cho đơn.");
            batchTransaction(batchId, orderItemId, "RESERVE", 0, take, onHand, reserved + take,
                    "reserve-" + orderItemId + "-" + batchId + "-" + UUID.randomUUID(), "Giữ hàng cho đơn", createdBy);
            remaining -= take;
        }
        if (remaining > 0) throw new IllegalArgumentException("Các lô hiện có không đủ hàng để giữ cho đơn.");
        if (jdbc.update("UPDATE variant_inventory SET reserved_quantity=reserved_quantity+? WHERE id=? AND quantity_on_hand-reserved_quantity>=?", quantity, inventoryId, quantity) != 1)
            throw new IllegalArgumentException("Tồn kho tổng vừa thay đổi. Vui lòng thử lại.");
    }

    @Transactional
    public void releaseOrderItem(Long orderItemId, String eventPrefix) {
        // Release only live reservations; a retry sees no RESERVED rows and cannot release twice.
        List<Map<String, Object>> allocations = jdbc.queryForList("SELECT a.batch_id,a.quantity,b.quantity_on_hand,b.reserved_quantity FROM order_item_batch_allocations a JOIN inventory_batches b ON b.id=a.batch_id WHERE a.order_item_id=? AND a.status='RESERVED' ORDER BY a.batch_id FOR UPDATE", orderItemId);
        for (Map<String, Object> allocation : allocations) {
            long batchId = ((Number) allocation.get("batch_id")).longValue();
            int quantity = ((Number) allocation.get("quantity")).intValue();
            int onHand = ((Number) allocation.get("quantity_on_hand")).intValue();
            int reserved = ((Number) allocation.get("reserved_quantity")).intValue();
            if (reserved < quantity || jdbc.update("UPDATE inventory_batches SET reserved_quantity=reserved_quantity-? WHERE id=? AND reserved_quantity>=?", quantity, batchId, quantity) != 1)
                throw new IllegalStateException("Số lượng giữ trong batch không còn nhất quán.");
            jdbc.update("UPDATE order_item_batch_allocations SET status='RELEASED' WHERE order_item_id=? AND batch_id=? AND status='RESERVED'", orderItemId, batchId);
            batchTransaction(batchId, orderItemId, "RELEASE", 0, -quantity, onHand, reserved - quantity,
                    eventPrefix + "-" + orderItemId + "-" + batchId + "-" + UUID.randomUUID(), "Giải phóng hàng giữ cho đơn", null);
        }
        int total = allocations.stream().mapToInt(row -> ((Number) row.get("quantity")).intValue()).sum();
        if (total > 0) {
            Long inventoryId = jdbc.queryForObject("SELECT inventory_id FROM inventory_batches WHERE id=?", Long.class,
                    ((Number) allocations.get(0).get("batch_id")).longValue());
            if (jdbc.update("UPDATE variant_inventory SET reserved_quantity=reserved_quantity-? WHERE id=? AND reserved_quantity>=?", total, inventoryId, total) != 1)
                throw new IllegalStateException("Tồn giữ tổng không còn nhất quán.");
        }
    }

    @Transactional
    public void consumeOrderItem(Long orderItemId, String orderCode) {
        // Consume only after explicit customer receipt confirmation; CONSUMED allocations make retries idempotent.
        List<Map<String, Object>> allocations = jdbc.queryForList("SELECT a.batch_id,a.quantity,b.quantity_on_hand,b.reserved_quantity FROM order_item_batch_allocations a JOIN inventory_batches b ON b.id=a.batch_id WHERE a.order_item_id=? AND a.status='RESERVED' ORDER BY a.batch_id FOR UPDATE", orderItemId);
        if (allocations.isEmpty()) {
            Integer consumed = jdbc.queryForObject("SELECT COUNT(*) FROM order_item_batch_allocations WHERE order_item_id=? AND status='CONSUMED'", Integer.class, orderItemId);
            if (consumed != null && consumed > 0) return;
            throw new IllegalStateException("Không tìm thấy batch đã giữ cho đơn hàng.");
        }
        for (Map<String, Object> allocation : allocations) {
            long batchId = ((Number) allocation.get("batch_id")).longValue();
            int quantity = ((Number) allocation.get("quantity")).intValue();
            int onHand = ((Number) allocation.get("quantity_on_hand")).intValue();
            int reserved = ((Number) allocation.get("reserved_quantity")).intValue();
            if (onHand < quantity || reserved < quantity || jdbc.update("UPDATE inventory_batches SET quantity_on_hand=quantity_on_hand-?,reserved_quantity=reserved_quantity-?,status=IF(quantity_on_hand-?=0,'CLOSED','ACTIVE') WHERE id=? AND quantity_on_hand>=? AND reserved_quantity>=?", quantity, quantity, quantity, batchId, quantity, quantity) != 1)
                throw new IllegalStateException("Không thể ghi nhận bán hàng từ batch.");
            jdbc.update("UPDATE order_item_batch_allocations SET status='CONSUMED' WHERE order_item_id=? AND batch_id=? AND status='RESERVED'", orderItemId, batchId);
            batchTransaction(batchId, orderItemId, "SALE", -quantity, -quantity, onHand - quantity, reserved - quantity,
                    "sale-" + orderItemId + "-" + batchId + "-" + UUID.randomUUID(), "Bán theo đơn " + orderCode, null);
        }
        int total = allocations.stream().mapToInt(row -> ((Number) row.get("quantity")).intValue()).sum();
        if (total > 0) {
            Long inventoryId = jdbc.queryForObject("SELECT inventory_id FROM inventory_batches WHERE id=?", Long.class,
                    ((Number) allocations.get(0).get("batch_id")).longValue());
            if (jdbc.update("UPDATE variant_inventory SET quantity_on_hand=quantity_on_hand-?,reserved_quantity=reserved_quantity-? WHERE id=? AND quantity_on_hand>=? AND reserved_quantity>=?", total, total, inventoryId, total, total) != 1)
                throw new IllegalStateException("Tồn tổng không còn nhất quán khi ghi nhận bán.");
        }
    }

    private void lockInventory(Long inventoryId) {
        List<Long> rows = jdbc.query("SELECT id FROM variant_inventory WHERE id=? FOR UPDATE", (rs, rowNum) -> rs.getLong(1), inventoryId);
        if (rows.isEmpty()) throw new InventoryActionException("Không tìm thấy biến thể cần thao tác.");
    }

    private void batchTransaction(Long batchId, Long orderItemId, String type, int onHandDelta, int reservedDelta,
                                  int onHandAfter, int reservedAfter, String eventKey, String reason, Long userId) {
        jdbc.update("INSERT INTO inventory_batch_transactions(batch_id,order_item_id,type,on_hand_delta,reserved_delta,on_hand_after,reserved_after,event_key,reason,created_by) VALUES(?,?,?,?,?,?,?,?,?,?)",
                batchId, orderItemId, type, onHandDelta, reservedDelta, onHandAfter, reservedAfter,
                eventKey, clean(reason), userId);
    }

    private String clean(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private int checkedAdd(int left, int right, String message) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ex) {
            throw new InventoryActionException(message);
        }
    }
}
