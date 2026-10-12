package repository;

import entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {

    /** Schema có UNIQUE (owner_id) nên tối đa một kết quả. */
    Optional<Shop> findByOwnerId(Long ownerId);
}
