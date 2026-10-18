package repository;

import entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    boolean existsByShopIdAndCode(Long shopId, String code);
    boolean existsByShopIdAndCodeAndIdNot(Long shopId, String code, Long id);
    Optional<Voucher> findByIdAndShopId(Long id, Long shopId);
    @Query("select v from Voucher v where v.shopId=:shopId and (:keyword='' or lower(v.code) like lower(concat('%',:keyword,'%')) or lower(v.name) like lower(concat('%',:keyword,'%'))) and (:status='' or v.status=:status) order by v.createdAt desc, v.id desc")
    List<Voucher> searchByShop(@Param("shopId") Long shopId, @Param("keyword") String keyword, @Param("status") String status);
}
