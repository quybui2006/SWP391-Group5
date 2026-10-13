package service;

import dto.PendingShopApprovalDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import repository.ShopRepository;

import java.util.List;

@Service
public class OperationsShopApprovalService {
    private final ShopRepository shops;

    public OperationsShopApprovalService(ShopRepository shops) {
        this.shops = shops;
    }

    @Transactional(readOnly = true)
    public List<PendingShopApprovalDto> findPendingShops() {
        return shops.findPendingApprovals();
    }

    @Transactional
    public void approveShop(Long shopId) {
        requireExistingShop(shopId);
        if (shops.approvePending(shopId) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Yêu cầu mở Shop không còn ở trạng thái chờ duyệt.");
        }
    }

    @Transactional
    public void rejectShop(Long shopId, String rejectionReason) {
        String reason = normalizeReason(rejectionReason);
        requireExistingShop(shopId);
        if (shops.rejectPending(shopId, reason) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Yêu cầu mở Shop không còn ở trạng thái chờ duyệt.");
        }
    }

    private void requireExistingShop(Long shopId) {
        if (shopId == null || !shops.existsById(shopId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu mở Shop.");
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
