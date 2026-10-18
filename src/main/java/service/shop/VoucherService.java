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

    public List<Voucher> list(Long ownerId,String keyword,String status){
        Long shopId=requireShop(ownerId).getId();
        String normalizedStatus=status==null?"":status.trim().toUpperCase(java.util.Locale.ROOT);
        if(!normalizedStatus.isEmpty()&&!List.of("ACTIVE","PAUSED","EXPIRED").contains(normalizedStatus)) normalizedStatus="";
        refreshExpired(shopId);
        return vouchers.searchByShop(shopId,keyword==null?"":keyword.trim(),normalizedStatus);
    }
    public Voucher getForEdit(Long ownerId,Long id){return vouchers.findByIdAndShopId(id,requireShop(ownerId).getId()).orElseThrow(()->new IllegalArgumentException("Không tìm thấy voucher thuộc cửa hàng của bạn."));}

    @Transactional
    public void save(Long ownerId,Long id,VoucherRequest form){
        Shop shop=requireShop(ownerId);
        String code=form.getCode()==null?"":form.getCode().trim().toUpperCase(java.util.Locale.ROOT);
        if(vouchers.existsByShopIdAndCodeAndIdNot(shop.getId(),code,id==null?-1L:id)) throw new IllegalArgumentException("Mã voucher này đã tồn tại trong cửa hàng của bạn.");
        if(!List.of("PERCENTAGE","FIXED_AMOUNT").contains(form.getDiscountType())) throw new IllegalArgumentException("Loại giảm giá không hợp lệ.");
        if(form.getStartsAt()==null||form.getEndsAt()==null||!form.getEndsAt().isAfter(form.getStartsAt())) throw new IllegalArgumentException("Ngày kết thúc phải sau ngày bắt đầu.");
        if("PERCENTAGE".equals(form.getDiscountType())&&(form.getDiscountValue()==null||form.getDiscountValue().compareTo(BigDecimal.ONE)<0||form.getDiscountValue().compareTo(new BigDecimal("100"))>0)) throw new IllegalArgumentException("Phần trăm giảm phải từ 1 đến 100.");
        if("FIXED_AMOUNT".equals(form.getDiscountType())&&form.getMaxDiscountAmount()!=null) throw new IllegalArgumentException("Voucher giảm số tiền không sử dụng mức giảm tối đa.");
        if(form.getMaxUsages()==null||form.getMaxUsages()<1) throw new IllegalArgumentException("Số lượt sử dụng tối đa phải ít nhất là 1.");
        Voucher v;
        if(id==null){v=new Voucher();v.setShopId(shop.getId());v.setUsedCount(0);v.setStatus("ACTIVE");}
        else {v=getForEdit(ownerId,id); if(v.getUsedCount()!=null&&form.getMaxUsages()<v.getUsedCount()) throw new IllegalArgumentException("Số lượt tối đa không thể nhỏ hơn số lượt đã sử dụng ("+v.getUsedCount()+").");}
        v.setCode(code);v.setName(form.getName().trim());v.setDiscountType(form.getDiscountType());v.setDiscountValue(form.getDiscountValue());
        v.setMaxDiscountAmount("PERCENTAGE".equals(form.getDiscountType())?form.getMaxDiscountAmount():null);
        v.setMinOrderValue(form.getMinOrderValue()==null?BigDecimal.ZERO:form.getMinOrderValue());v.setMaxUsages(form.getMaxUsages());v.setIsPublic(Boolean.TRUE.equals(form.getIsPublic()));
        v.setStartsAt(form.getStartsAt());v.setEndsAt(form.getEndsAt());
        if(v.getEndsAt().isBefore(LocalDateTime.now(BUSINESS_ZONE)))v.setStatus("EXPIRED");
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
    private void refreshExpired(Long shopId){List<Voucher> expired=vouchers.searchByShop(shopId,"","").stream().filter(v->!"EXPIRED".equals(v.getStatus())&&v.getEndsAt()!=null&&!v.getEndsAt().isAfter(LocalDateTime.now(BUSINESS_ZONE))).toList();expired.forEach(v->v.setStatus("EXPIRED"));if(!expired.isEmpty())vouchers.saveAll(expired);}
    private Shop requireShop(Long ownerId){Shop s=shops.findByOwnerId(ownerId).orElseThrow(()->new IllegalArgumentException("Tài khoản chưa có cửa hàng."));if(!"APPROVED".equals(s.getApprovalStatus()))throw new IllegalArgumentException("Cửa hàng cần được duyệt trước khi quản lý voucher.");return s;}
}
