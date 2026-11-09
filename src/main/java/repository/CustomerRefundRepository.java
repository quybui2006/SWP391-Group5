package repository;

import entity.CustomerRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRefundRepository extends JpaRepository<CustomerRefund, Long> {

    boolean existsByOrderIdAndRefundType(Long orderId, String refundType);
}
