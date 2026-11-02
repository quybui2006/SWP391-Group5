package repository;

import entity.UserAddresses;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserAddressesRepository extends JpaRepository<UserAddresses, Long> {
    List<UserAddresses> findByUserIdOrderByDefaultAddressDescCreatedAtDesc(Long userId);
    Optional<UserAddresses> findByIdAndUserId(Long id, Long userId);
}
