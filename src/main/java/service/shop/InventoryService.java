package service.shop;

import dto.shop.InventoryActionRequest;
import dto.shop.InventoryActionType;
import dto.shop.InventoryAdjustmentDirection;
import dto.shop.InventoryRowDto;
import dto.shop.InventoryTransactionRowDto;
import entity.InventoryTransaction;
import entity.ProductVariant;
import entity.Shop;
import entity.VariantInventory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import repository.InventoryTransactionRepository;
import repository.ProductVariantRepository;
import repository.ShopRepository;
import repository.VariantInventoryRepository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class InventoryService {

    private final ShopRepository shopRepository;
    private final ProductVariantRepository productVariantRepository;
    private final VariantInventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;

    public InventoryService(ShopRepository shopRepository,
                            ProductVariantRepository productVariantRepository,
                            VariantInventoryRepository inventoryRepository,
                            InventoryTransactionRepository transactionRepository) {
        this.shopRepository = shopRepository;
        this.productVariantRepository = productVariantRepository;
        this.inventoryRepository = inventoryRepository;
        this.transactionRepository = transactionRepository;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<InventoryRowDto> getInventoryRows(Long ownerUserId) {
        return inventoryRepository.findInventoryRowsForShop(requireShop(ownerUserId).getId());
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<InventoryTransactionRowDto> getTransactionHistory(Long ownerUserId) {
        return transactionRepository.findHistoryForShop(requireShop(ownerUserId).getId());
    }

    /**
     * Xử lý thay đổi tồn kho theo mô hình sổ cái.
     * Cập nhật quantity_on_hand và tạo inventory_transactions trong cùng transaction.
     * available_quantity là cột generated nên tuyệt đối không được ghi.
     *
     * ownerUserId được lấy từ session phía Controller, không lấy từ form để tránh giả mạo người tạo.
     */
    @Transactional
    public void processInventoryAction(InventoryActionRequest request, Long ownerUserId) {
        if (request == null) throw new InventoryActionException("Dữ liệu giao dịch không hợp lệ.");
        validateRequest(request);

        Shop shop = requireShop(ownerUserId);
        ProductVariant variant = productVariantRepository.findById(request.getVariantId())
                .orElseThrow(() -> new InventoryActionException("Không tìm thấy biến thể sản phẩm."));
        if (!Objects.equals(variant.getShopId(), shop.getId())) {
            throw new InventoryActionException("Bạn không có quyền thay đổi tồn kho của biến thể này.");
        }

        // PESSIMISTIC_WRITE: thao tác trên cùng một biến thể sẽ được tuần tự hóa trong database.
        VariantInventory inventory = inventoryRepository.findLockedByVariantId(request.getVariantId())
                .orElseThrow(() -> new InventoryActionException(
                        "Biến thể này chưa có bản ghi tồn kho. Vui lòng khởi tạo tồn kho trước."));

        int quantity = request.getQuantity();
        int onHand = inventory.getQuantityOnHand();
        int reserved = inventory.getReservedQuantity();
        int available = onHand - reserved;
        int onHandDelta;
        String type = request.getActionType().name();
        String reason = normalizeReason(request.getReason());

        switch (request.getActionType()) {
            case RECEIVE -> {
                // Tăng số lượng shop khai báo trên sàn; ghi chú có thể để trống.
                onHandDelta = quantity;
            }
            case DISCARD -> {
                requireReason(reason);
                if (quantity > available) {
                    throw new InventoryActionException("Không đủ số lượng có thể bán để báo hỏng. Hiện chỉ còn "
                            + available + " sản phẩm khả dụng.");
                }
                onHandDelta = -quantity;
            }
            case ADJUST -> {
                requireReason(reason);
                InventoryAdjustmentDirection direction = request.getAdjustmentDirection();
                if (direction == null) {
                    throw new InventoryActionException("Vui lòng chọn hướng điều chỉnh tăng hoặc giảm tồn.");
                }
                if (direction == InventoryAdjustmentDirection.INCREASE) {
                    onHandDelta = quantity;
                } else {
                    if (quantity > available) {
                        throw new InventoryActionException("Không thể giảm nhiều hơn số lượng khả dụng ("
                                + available + "). Hàng đang được khách giữ không thể bị điều chỉnh giảm.");
                    }
                    onHandDelta = -quantity;
                }
            }
            default -> throw new InventoryActionException("Loại giao dịch không được hỗ trợ.");
        }

        final int onHandAfter;
        try {
            onHandAfter = Math.addExact(onHand, onHandDelta);
        } catch (ArithmeticException ex) {
            throw new InventoryActionException("Số lượng sau điều chỉnh vượt giới hạn cho phép.");
        }
        if (onHandAfter < 0 || onHandAfter < reserved) {
            throw new InventoryActionException("Số lượng tồn kho trên sàn không được thấp hơn số lượng đang giữ cho đơn hàng.");
        }

        // Không gọi setAvailableQuantity: đây là cột GENERATED ALWAYS AS của MySQL.
        inventory.setQuantityOnHand(onHandAfter);
        inventoryRepository.save(inventory);

        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setInventoryId(inventory.getId());
        transaction.setOrderItemId(null); // Giao dịch được khởi tạo thủ công, không gắn với order item.
        transaction.setType(type);
        transaction.setOnHandDelta(onHandDelta);
        transaction.setReservedDelta(0);
        transaction.setOnHandAfter(onHandAfter);
        transaction.setReservedAfter(reserved);
        transaction.setEventKey("SHOP_OWNER:" + UUID.randomUUID());
        transaction.setReason(reason);
        transaction.setCreatedBy(ownerUserId);
        transactionRepository.save(transaction);
    }

    private void validateRequest(InventoryActionRequest request) {
        if (request.getVariantId() == null) throw new InventoryActionException("Vui lòng chọn biến thể sản phẩm.");
        if (request.getActionType() == null) throw new InventoryActionException("Vui lòng chọn loại giao dịch.");
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new InventoryActionException("Số lượng phải là số nguyên dương.");
        }
        if (request.getReason() != null && request.getReason().length() > 1000) {
            throw new InventoryActionException("Lý do không được vượt quá 1000 ký tự.");
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null) return null;
        String normalized = reason.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InventoryActionException("Bắt buộc nhập lý do khi báo hỏng hoặc điều chỉnh số lượng tồn kho.");
        }
    }

    private Shop requireShop(Long ownerUserId) {
        if (ownerUserId == null) throw new InventoryActionException("Phiên đăng nhập không hợp lệ.");
        return shopRepository.findByOwnerId(ownerUserId)
                .orElseThrow(() -> new InventoryActionException("Tài khoản chưa có cửa hàng hợp lệ."));
    }
}
