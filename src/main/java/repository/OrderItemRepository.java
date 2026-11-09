package repository;

import entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    Optional<OrderItem> findByIdAndOrderId(Long id, Long orderId);

    List<OrderItem> findByOrderIdOrderByIdAsc(Long orderId);
}
