package service;

import dto.PendingProductApprovalDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import repository.ShopProductRepository;

import java.util.List;

@Service
public class OperationsProductApprovalService {
    private final ShopProductRepository products;

    public OperationsProductApprovalService(ShopProductRepository products) {
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<PendingProductApprovalDto> findPendingProducts() {
        return products.findPendingApprovals();
    }

    @Transactional
    public void approveProduct(Long productId) {
        requireExistingProduct(productId);
        if (products.approvePending(productId) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Yêu cầu đăng sản phẩm không còn ở trạng thái chờ duyệt.");
        }
    }

    @Transactional
    public void rejectProduct(Long productId, String rejectionReason) {
        String reason = normalizeReason(rejectionReason);
        requireExistingProduct(productId);
        if (products.rejectPending(productId, reason) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Yêu cầu đăng sản phẩm không còn ở trạng thái chờ duyệt.");
        }
    }

    private void requireExistingProduct(Long productId) {
        if (productId == null || !products.existsById(productId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đăng sản phẩm.");
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do từ chối.");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Lý do từ chối không được vượt quá 1000 ký tự.");
        }
        return trimmed;
    }
}
