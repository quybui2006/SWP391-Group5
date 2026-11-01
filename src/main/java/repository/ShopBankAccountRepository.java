package repository;

import entity.ShopBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShopBankAccountRepository extends JpaRepository<ShopBankAccount, Long> {

    List<ShopBankAccount> findByShopIdOrderByIsDefaultDescIdDesc(Long shopId);

    Optional<ShopBankAccount> findByIdAndShopId(Long id, Long shopId);

    Optional<ShopBankAccount> findFirstByShopIdAndIsDefaultTrue(Long shopId);

    long countByShopId(Long shopId);

    boolean existsByShopIdAndBankAccountNumber(Long shopId, String bankAccountNumber);

    List<ShopBankAccount> findByApprovalStatus(String approvalStatus);

    @Query("SELECT DISTINCT b.shopId FROM ShopBankAccount b WHERE b.approvalStatus = :approvalStatus")
    List<Long> findDistinctShopIdsByApprovalStatus(@Param("approvalStatus") String approvalStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ShopBankAccount b SET b.isDefault = false WHERE b.shopId = :shopId")
    void resetDefaultByShopId(@Param("shopId") Long shopId);
}

