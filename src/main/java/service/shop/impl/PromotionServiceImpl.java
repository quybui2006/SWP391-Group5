package service.shop.impl;

import dto.shop.PromotionForm;
import dto.shop.PromotionListItem;
import dto.shop.PromotionVariantOption;
import entity.ProductVariant;
import entity.PromotionalPrice;
import entity.Shop;
import entity.VariantInventory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.ProductVariantRepository;
import repository.PromotionalPriceRepository;
import repository.ShopRepository;
import repository.VariantInventoryRepository;
import service.shop.PromotionValidationException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PromotionServiceImpl {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final PromotionalPriceRepository promotionRepository;
    private final ProductVariantRepository variantRepository;
    private final VariantInventoryRepository inventoryRepository;
    private final ShopRepository shopRepository;
    private final JdbcTemplate jdbc;

    public PromotionServiceImpl(PromotionalPriceRepository promotionRepository,
            ProductVariantRepository variantRepository, VariantInventoryRepository inventoryRepository,
            ShopRepository shopRepository, JdbcTemplate jdbc) {
        this.promotionRepository = promotionRepository;
        this.variantRepository = variantRepository;
        this.inventoryRepository = inventoryRepository;
        this.shopRepository = shopRepository;
        this.jdbc = jdbc;
    }

    /** Đọc các chương trình của shop và trả tên sản phẩm, giá, lịch chạy cho màn danh sách. */
    public List<PromotionListItem> getPromotions(Long ownerUserId, String keyword) {
        Long shopId = requireShop(ownerUserId).getId();
        String term = keyword == null ? "" : keyword.trim().toLowerCase();
        return jdbc.query("""
                SELECT pp.id, pp.variant_id, p.name AS product_name, pv.name AS variant_name, pv.sku,
                       pv.price AS original_price, pp.promotional_price, pp.starts_at, pp.ends_at, pp.is_enabled
                FROM promotional_prices pp
                JOIN product_variants pv ON pv.id = pp.variant_id
                JOIN products p ON p.id = pv.product_id
                WHERE pv.shop_id = ? AND (? = '' OR LOWER(p.name) LIKE ? OR LOWER(pv.name) LIKE ? OR LOWER(pv.sku) LIKE ?)
                ORDER BY pp.created_at DESC, pp.id DESC
                """, (rs, row) -> new PromotionListItem(
                rs.getLong("id"), rs.getLong("variant_id"), rs.getString("product_name"),
                rs.getString("variant_name"), rs.getString("sku"), rs.getBigDecimal("original_price"),
                rs.getBigDecimal("promotional_price"), toLocalDateTime(rs.getTimestamp("starts_at")),
                toLocalDateTime(rs.getTimestamp("ends_at")), rs.getBoolean("is_enabled")),
                shopId, term, "%" + term + "%", "%" + term + "%", "%" + term + "%");
    }

    /** Lấy các biến thể đủ điều kiện để chủ shop chọn khi tạo khuyến mãi. */
    public List<PromotionVariantOption> getVariantOptions(Long ownerUserId) {
        Long shopId = requireShop(ownerUserId).getId();
        return variantRepository.findPromotionEligibleVariants(shopId).stream()
                .map(v -> new PromotionVariantOption(v.getId(), v.getProduct().getName() + " — " + v.getName() + " (" + v.getSku() + ")", v.getPrice()))
                .toList();
    }

    /** Nạp dữ liệu chương trình thuộc shop vào form chỉnh sửa. */
    public PromotionForm getForm(Long id, Long ownerUserId) {
        PromotionalPrice promotion = loadOwnedPromotion(id, ownerUserId);
        PromotionForm form = new PromotionForm();
        form.setVariantId(promotion.getVariantId());
        form.setPromotionalPrice(promotion.getPromotionalPrice());
        form.setStartsAt(promotion.getStartsAt());
        form.setEndsAt(promotion.getEndsAt());
        form.setEnabled(promotion.getEnabled());
        return form;
    }

    /** Kiểm tra quyền sở hữu, giá, thời gian, tồn kho và lưu lịch khuyến mãi không chồng lấn. */
    @Transactional
    public void save(PromotionForm form, Long ownerUserId, Long promotionId) {
        try {
            ProductVariant variant = variantRepository.findById(form.getVariantId())
                    .orElseThrow(() -> new PromotionValidationException("variantId", "Biến thể sản phẩm không tồn tại."));
            Shop shop = requireShop(ownerUserId);
            if (!shop.getId().equals(variant.getShopId())) {
                throw new PromotionValidationException("variantId", "Bạn chỉ có thể tạo khuyến mãi cho sản phẩm thuộc cửa hàng của mình.");
            }
            if (!"ACTIVE".equals(variant.getStatus())
                    || !("APPROVED".equals(variant.getApprovalStatus()) || "PENDING".equals(variant.getApprovalStatus()))) {
                throw new PromotionValidationException("variantId", "Chỉ có thể tạo khuyến mãi cho biến thể đang bán và đã đăng lên sàn.");
            }
            if (form.getPromotionalPrice() == null || form.getPromotionalPrice().compareTo(variant.getPrice()) >= 0) {
                throw new PromotionValidationException("promotionalPrice", "Giá khuyến mãi phải lớn hơn 0 và thấp hơn giá bán hiện tại (" + variant.getPrice().toPlainString() + ").");
            }
            LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
            if (form.getStartsAt() == null || form.getStartsAt().isBefore(now)) {
                throw new PromotionValidationException("startsAt", "Thời điểm bắt đầu phải từ thời điểm hiện tại trở đi (giờ Việt Nam). Hãy chọn lại thời gian.");
            }
            if (form.getEndsAt() == null || !form.getEndsAt().isAfter(form.getStartsAt())) {
                throw new PromotionValidationException("endsAt", "Thời điểm kết thúc phải sau thời điểm bắt đầu.");
            }
            // Khóa dòng biến thể để hai yêu cầu đồng thời không cùng vượt qua bước kiểm tra lịch trùng.
            jdbc.queryForObject("SELECT id FROM product_variants WHERE id=? FOR UPDATE", Long.class, variant.getId());
            VariantInventory inventory = inventoryRepository.findByVariantId(variant.getId())
                    .orElseThrow(() -> new PromotionValidationException("variantId", "Biến thể này chưa có thông tin kho hàng."));
            if (!"ACTIVE".equals(inventory.getStatus())) {
                throw new PromotionValidationException("variantId", "Kho của biến thể này chưa ở trạng thái hoạt động.");
            }
            Long excludedId = promotionId == null ? -1L : promotionId;
            Integer overlaps = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM promotional_prices
                    WHERE variant_id = ? AND is_enabled = 1 AND id <> ?
                      AND starts_at < ? AND ends_at > ?
                    """, Integer.class, variant.getId(), excludedId,
                    Timestamp.valueOf(form.getEndsAt()), Timestamp.valueOf(form.getStartsAt()));
            if (overlaps != null && overlaps > 0 && Boolean.TRUE.equals(form.getEnabled())) {
                throw new PromotionValidationException("startsAt", "Biến thể này đã có chương trình khuyến mãi đang bật trong khoảng thời gian được chọn.");
            }

            PromotionalPrice promotion = promotionId == null ? new PromotionalPrice() : loadOwnedPromotion(promotionId, ownerUserId);
            promotion.setVariantId(variant.getId());
            promotion.setInventoryId(inventory.getId());
            promotion.setPromotionalPrice(form.getPromotionalPrice());
            promotion.setStartsAt(form.getStartsAt());
            promotion.setEndsAt(form.getEndsAt());
            promotion.setEnabled(form.getEnabled() == null || form.getEnabled());
            if (promotionId == null) promotion.setCreatedBy(ownerUserId);
            promotionRepository.save(promotion);
        } catch (DataAccessException ex) {
            throw new PromotionValidationException("serviceError", "Không thể lưu khuyến mãi do dữ liệu liên quan không hợp lệ. Vui lòng kiểm tra lại và thử lại.");
        }
    }

    /** Bật hoặc tắt lịch; khi bật phải kiểm tra lịch còn hạn và không trùng với lịch khác. */
    @Transactional
    public void toggle(Long id, Long ownerUserId) {
        PromotionalPrice promotion = loadOwnedPromotion(id, ownerUserId);
        boolean enable = !Boolean.TRUE.equals(promotion.getEnabled());
        if (enable) {
            // Dùng cùng khóa biến thể với hàm lưu để thao tác bật/tạo được tuần tự hóa.
            jdbc.queryForObject("SELECT id FROM product_variants WHERE id=? FOR UPDATE", Long.class, promotion.getVariantId());
            LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
            if (!promotion.getEndsAt().isAfter(now)) {
                throw new PromotionValidationException("serviceError", "Không thể bật khuyến mãi đã hết hạn. Hãy chỉnh sửa thời gian kết thúc trước.");
            }
            Integer overlap = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM promotional_prices
                    WHERE variant_id = ? AND id <> ? AND is_enabled = 1
                      AND starts_at < ? AND ends_at > ?
                    """, Integer.class, promotion.getVariantId(), id,
                    Timestamp.valueOf(promotion.getEndsAt()), Timestamp.valueOf(promotion.getStartsAt()));
            if (overlap != null && overlap > 0) throw new PromotionValidationException("serviceError", "Không thể bật vì thời gian bị trùng với một khuyến mãi khác của biến thể này.");
        }
        promotion.setEnabled(enable);
        promotionRepository.save(promotion);
    }

    /** Chỉ xóa lịch chưa được dùng trong đơn để không làm mất giá đã chốt trong lịch sử. */
    @Transactional
    public void delete(Long id, Long ownerUserId) {
        PromotionalPrice promotion = loadOwnedPromotion(id, ownerUserId);
        Integer usedInOrders = jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_items WHERE promotion_id = ?", Integer.class, id);
        if (usedInOrders != null && usedInOrders > 0) {
            throw new PromotionValidationException("serviceError",
                    "Khuyến mãi đã được dùng trong đơn hàng nên không thể xóa để bảo toàn lịch sử. Bạn có thể tắt khuyến mãi này.");
        }
        promotionRepository.delete(promotion);
    }

    /** Tải chương trình và xác nhận biến thể thuộc cửa hàng của tài khoản đang thao tác. */
    private PromotionalPrice loadOwnedPromotion(Long id, Long ownerUserId) {
        PromotionalPrice promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new PromotionValidationException("serviceError", "Không tìm thấy chương trình khuyến mãi."));
        ProductVariant variant = variantRepository.findById(promotion.getVariantId())
                .orElseThrow(() -> new PromotionValidationException("serviceError", "Không tìm thấy biến thể sản phẩm."));
        if (!requireShop(ownerUserId).getId().equals(variant.getShopId())) {
            throw new PromotionValidationException("serviceError", "Bạn không có quyền thao tác với khuyến mãi này.");
        }
        return promotion;
    }

    /** Tìm cửa hàng của tài khoản; báo lỗi rõ ràng khi tài khoản chưa có shop. */
    private Shop requireShop(Long ownerUserId) {
        return shopRepository.findByOwnerId(ownerUserId)
                .orElseThrow(() -> new PromotionValidationException("serviceError", "Tài khoản chưa có cửa hàng hợp lệ."));
    }

    /** Chuyển thời gian JDBC sang LocalDateTime để hiển thị trong form. */
    private LocalDateTime toLocalDateTime(Timestamp value) { return value == null ? null : value.toLocalDateTime(); }
}
