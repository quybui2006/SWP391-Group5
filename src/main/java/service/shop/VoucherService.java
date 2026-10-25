package service.shop;

import dto.shop.VoucherRequest;
import entity.Shop;
import entity.Voucher;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import repository.ShopRepository;
import repository.VoucherRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class VoucherService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final VoucherRepository vouchers;
    private final ShopRepository shops;
    public VoucherService(VoucherRepository vouchers, ShopRepository shops){this.vouchers=vouchers;this.shops=shops;}

    public List<Voucher> list(Long ownerId,String keyword,String status){ return list(ownerId, keyword, status, "DISCOUNT"); }
    public List<Voucher> list(Long ownerId,String keyword,String status,String voucherType){
        Long shopId=requireShop(ownerId).getId();
        String normalizedStatus=status==null?"":status.trim().toUpperCase(java.util.Locale.ROOT);
        if(!normalizedStatus.isEmpty()&&!List.of("ACTIVE","PAUSED","EXPIRED").contains(normalizedStatus)) normalizedStatus="";
        refreshExpired(shopId);
        return vouchers.searchByShop(shopId,keyword==null?"":keyword.trim(),normalizedStatus, voucherType);
    }
    public Voucher getForEdit(Long ownerId,Long id){return vouchers.findByIdAndShopId(id,requireShop(ownerId).getId()).orElseThrow(()->new IllegalArgumentException("Không tìm thấy voucher thuộc cửa hàng của bạn."));}

    @Transactional
    public void save(Long ownerId,Long id,VoucherRequest form){
        Shop shop = requireShop(ownerId);
        String code = form.getCode() == null ? "" : form.getCode().trim().toUpperCase(java.util.Locale.ROOT);
        String name = form.getName() == null ? "" : form.getName().trim();
        if (code.isBlank() || code.length() > 50 || !code.matches("[A-Z0-9]+(?:[A-Z0-9_-]*[A-Z0-9])?")) {
            throw new IllegalArgumentException("Mã voucher chỉ gồm chữ in hoa không dấu, số, dấu gạch ngang hoặc gạch dưới; không được để trống và tối đa 50 ký tự.");
        }
        if (name.isBlank() || name.length() > 150) {
            throw new IllegalArgumentException("Tên voucher không được để trống và tối đa 150 ký tự.");
        }
        if (vouchers.existsByShopIdAndCodeAndIdNot(shop.getId(), code, id == null ? -1L : id)) {
            throw new IllegalArgumentException("Mã voucher này đã tồn tại trong cửa hàng của bạn.");
        }
        if (!"CASHBACK".equals(form.getVoucherType()) && !"DISCOUNT".equals(form.getVoucherType())) {
            throw new IllegalArgumentException("Loại voucher không hợp lệ.");
        }
        if ("CASHBACK".equals(form.getVoucherType()) && !"PERCENTAGE".equals(form.getDiscountType())) {
            throw new IllegalArgumentException("Voucher hoàn xu chỉ hỗ trợ giảm theo phần trăm.");
        }
        if (!"PERCENTAGE".equals(form.getDiscountType()) && !"FIXED_AMOUNT".equals(form.getDiscountType())) {
            throw new IllegalArgumentException("Loại giảm giá không hợp lệ.");
        }
        BigDecimal discountValue = form.getDiscountValue();
        if (discountValue == null || discountValue.compareTo(BigDecimal.ZERO) <= 0 || !isWholeVnd(discountValue)) {
            throw new IllegalArgumentException("Giá trị giảm phải là số nguyên VNĐ lớn hơn 0.");
        }
        if ("PERCENTAGE".equals(form.getDiscountType())
                && discountValue.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Phần trăm giảm phải từ 1 đến 100.");
        }
        if ("PERCENTAGE".equals(form.getDiscountType()) && form.getMaxDiscountAmount() != null
                && (form.getMaxDiscountAmount().compareTo(BigDecimal.ZERO) <= 0 || !isWholeVnd(form.getMaxDiscountAmount()))) {
            throw new IllegalArgumentException("Mức giảm tối đa phải là số nguyên VNĐ lớn hơn 0 hoặc để trống.");
        }
        if ("FIXED_AMOUNT".equals(form.getDiscountType()) && form.getMaxDiscountAmount() != null) {
            throw new IllegalArgumentException("Voucher giảm số tiền không sử dụng mức giảm tối đa.");
        }
        BigDecimal minOrderValue = form.getMinOrderValue() == null ? BigDecimal.ZERO : form.getMinOrderValue();
        if (minOrderValue.compareTo(BigDecimal.ZERO) < 0 || !isWholeVnd(minOrderValue)) {
            throw new IllegalArgumentException("Giá trị đơn hàng tối thiểu phải là số nguyên VNĐ không âm.");
        }
        if (form.getMaxUsages() == null || form.getMaxUsages() < 1) {
            throw new IllegalArgumentException("Số lượt sử dụng tối đa phải ít nhất là 1.");
        }
        if (form.getStartsAt() == null || form.getEndsAt() == null
                || !form.getEndsAt().isAfter(form.getStartsAt())) {
            throw new IllegalArgumentException("Ngày kết thúc phải sau ngày bắt đầu.");
        }
        Voucher v;
        if(id==null){v=new Voucher();v.setShopId(shop.getId());v.setUsedCount(0);v.setStatus("ACTIVE");}
        else {v=getForEdit(ownerId,id); if(v.getUsedCount()!=null&&form.getMaxUsages()<v.getUsedCount()) throw new IllegalArgumentException("Số lượt tối đa không thể nhỏ hơn số lượt đã sử dụng ("+v.getUsedCount()+").");}
        v.setCode(code);v.setName(name);v.setVoucherType(form.getVoucherType());v.setDiscountType(form.getDiscountType());v.setDiscountValue(discountValue);
        v.setMaxDiscountAmount("PERCENTAGE".equals(form.getDiscountType())?form.getMaxDiscountAmount():null);
        v.setMinOrderValue(minOrderValue);v.setMaxUsages(form.getMaxUsages());v.setIsPublic(Boolean.TRUE.equals(form.getIsPublic()));
        v.setStartsAt(form.getStartsAt());v.setEndsAt(form.getEndsAt());
        if(!v.getEndsAt().isAfter(LocalDateTime.now(BUSINESS_ZONE)))v.setStatus("EXPIRED");
        else if(id==null||"EXPIRED".equals(v.getStatus()))v.setStatus("ACTIVE");
        try {vouchers.saveAndFlush(v);} catch(DataIntegrityViolationException ex){throw new IllegalArgumentException("Không thể lưu voucher. Hãy kiểm tra mã trùng và các giá trị bắt buộc.");}
    }
    @Transactional
    public void deleteUnusedVoucher(Long ownerId, Long voucherId) {
        Voucher voucher = getForEdit(ownerId, voucherId);
        int used = voucher.getUsedCount() == null ? 0 : voucher.getUsedCount();
        if (used > 0) {
            throw new IllegalArgumentException("Voucher đã được sử dụng " + used
                    + " lần nên không thể xóa để bảo toàn lịch sử. Bạn có thể tắt voucher thay thế.");
        }
        try {
            vouchers.delete(voucher);
            vouchers.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("Voucher đang được dữ liệu khác tham chiếu nên không thể xóa. Hãy tắt voucher thay thế.");
        }
    }

    @Transactional
    public String toggleVoucherStatus(Long ownerId,Long voucherId){
        Voucher v=getForEdit(ownerId,voucherId);
        if(!v.getEndsAt().isAfter(LocalDateTime.now(BUSINESS_ZONE))||"EXPIRED".equals(v.getStatus())){v.setStatus("EXPIRED");vouchers.save(v);throw new IllegalArgumentException("Voucher đã hết hạn nên không thể bật hoặc tắt.");}
        if(v.getUsedCount()!=null&&v.getMaxUsages()!=null&&v.getUsedCount()>=v.getMaxUsages())throw new IllegalArgumentException("Voucher đã hết lượt sử dụng nên không thể bật lại.");
        v.setStatus("ACTIVE".equals(v.getStatus())?"PAUSED":"ACTIVE");vouchers.save(v);return v.getStatus();
    }
    private boolean isWholeVnd(BigDecimal value) {
        if (value == null) return false;
        BigDecimal normalized = value.stripTrailingZeros();
        int integerDigits = Math.max(0, normalized.precision() - normalized.scale());
        return normalized.scale() <= 0 && integerDigits <= 19;
    }

    private void refreshExpired(Long shopId){List<Voucher> expired=vouchers.searchByShop(shopId,"","","DISCOUNT"); expired = new java.util.ArrayList<>(expired); expired.addAll(vouchers.searchByShop(shopId,"","","CASHBACK")); expired.stream().filter(v->!"EXPIRED".equals(v.getStatus())&&v.getEndsAt()!=null&&!v.getEndsAt().isAfter(LocalDateTime.now(BUSINESS_ZONE))).forEach(v->v.setStatus("EXPIRED"));if(!expired.isEmpty())vouchers.saveAll(expired);}
    private Shop requireShop(Long ownerId){Shop s=shops.findByOwnerId(ownerId).orElseThrow(()->new IllegalArgumentException("Tài khoản chưa có cửa hàng."));if(!"APPROVED".equals(s.getApprovalStatus()))throw new IllegalArgumentException("Cửa hàng cần được duyệt trước khi quản lý voucher.");return s;}
}
