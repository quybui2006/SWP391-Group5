package repository;

import entity.CustomerSavedVoucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerSavedVoucherRepository extends JpaRepository<CustomerSavedVoucher, Long> {

    boolean existsByUserIdAndVoucherId(Long userId, Long voucherId);
}
