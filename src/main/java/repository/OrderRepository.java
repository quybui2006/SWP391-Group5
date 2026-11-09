package repository;

import entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("""
            SELECT o
            FROM Order o, Checkout c
            WHERE o.checkoutId = c.id AND o.id = :orderId AND c.userId = :customerId
            """)
    Optional<Order> findByIdAndCustomerId(@Param("orderId") Long orderId, @Param("customerId") Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT o
            FROM Order o, Checkout c
            WHERE o.checkoutId = c.id AND o.id = :orderId AND c.userId = :customerId
            """)
    Optional<Order> findLockedByIdAndCustomerId(@Param("orderId") Long orderId, @Param("customerId") Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :orderId")
    Optional<Order> findLockedById(@Param("orderId") Long orderId);
}
