package service;

import dto.ShopOwnerCategoryOption;
import dto.ShopOwnerProductForm;
import dto.ShopOwnerProductView;
import dto.ShopOwnerProductListItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.ShopOwnerProductScreenRepository;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ShopOwnerProductScreenService {
    private static final Set<String> APPROVALS = Set.of("DRAFT", "PENDING", "APPROVED");
    private static final Set<String> SELLING = Set.of("DRAFT", "ACTIVE", "PAUSED", "ARCHIVED");
    private final ShopOwnerProductScreenRepository repository;

    public ShopOwnerProductScreenService(ShopOwnerProductScreenRepository repository) {
        this.repository = repository;
    }

    public ShopOwnerProductView getDetail(Long productId, Long userId) {
        Long ownerId = resolveOwner(userId);
        return repository.findOwnedProduct(productId, ownerId)
                .orElseThrow(ShopOwnerProductScreenRepository.ShopOwnerProductNotFoundException::new);
    }

    public List<ShopOwnerProductListItem> getProducts(Long userId) {
        return repository.findOwnedProducts(resolveOwner(userId));
    }

    public List<ShopOwnerCategoryOption> getCategories() {
        return repository.findActiveCategories();
    }

    @Transactional
    public void update(Long productId, Long userId, ShopOwnerProductForm form) {
        Long ownerId = resolveOwner(userId);
        Map<String, String> errors = validate(form);
        if (!errors.isEmpty()) {
            throw new ShopOwnerProductValidationException(errors);
        }
        repository.updateOwnedProduct(productId, ownerId, form);
    }

    private Map<String, String> validate(ShopOwnerProductForm form) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (form.getName() == null || form.getName().isBlank()) {
            errors.put("name", "Tên sản phẩm không được để trống.");
        } else if (form.getName().trim().length() > 200) {
            errors.put("name", "Tên sản phẩm tối đa 200 ký tự.");
        }
        if (form.getOrigin() != null && form.getOrigin().trim().length() > 150) {
            errors.put("origin", "Xuất xứ tối đa 150 ký tự.");
        }
        if (form.getDescription() != null && form.getDescription().trim().length() > 2000) {
            errors.put("description", "Mô tả tối đa 2000 ký tự.");
        }
        LocalDate received = form.getReceivedDate();
        LocalDate expiry = form.getExpiryDate();
        if (received == null) errors.put("receivedDate", "Vui lòng chọn ngày nhập kho.");
        if (expiry == null) errors.put("expiryDate", "Vui lòng chọn hạn sử dụng.");
        if (received != null && received.isAfter(LocalDate.now())) {
            errors.put("receivedDate", "Ngày nhập kho không được ở tương lai.");
        }
        if (received != null && expiry != null && expiry.isBefore(received)) {
            errors.put("expiryDate", "Hạn sử dụng phải từ ngày nhập kho trở đi.");
        }
        String approval = upper(form.getApprovalStatus());
        String selling = upper(form.getSellingStatus());
        if (!APPROVALS.contains(approval)) errors.put("approvalStatus", "Trạng thái duyệt không hợp lệ.");
        if (!SELLING.contains(selling)) errors.put("sellingStatus", "Trạng thái bán không hợp lệ.");
        if ("ACTIVE".equals(selling) && !"APPROVED".equals(approval)) {
            errors.put("sellingStatus", "Chỉ được mở bán sản phẩm đã được duyệt.");
        }
        if (form.getCategoryId() == null || !repository.categoryExists(form.getCategoryId())) {
            errors.put("categoryId", "Vui lòng chọn danh mục đang hoạt động.");
        }
        return errors;
    }

    private Long resolveOwner(Long userId) {
        if (userId != null) return userId;
        // Tạm phục vụ demo trước khi toàn bộ luồng Login/Shop Owner hoàn thiện.
        Long demoOwnerId = repository.findFirstShopOwnerId();
        if (demoOwnerId == null) throw new ShopOwnerProductNotFoundException();
        return demoOwnerId;
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    public static class ShopOwnerProductValidationException extends RuntimeException {
        private final Map<String, String> errors;
        public ShopOwnerProductValidationException(Map<String, String> errors) { this.errors = errors; }
        public Map<String, String> getErrors() { return errors; }
    }

    public static class ShopOwnerProductNotFoundException extends RuntimeException { }
}
