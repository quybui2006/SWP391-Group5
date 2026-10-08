package repository;

import entity.EmailOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {
    Optional<EmailOtp> findFirstByEmailIgnoreCaseAndPurposeOrderByIdDesc(
            String email, String purpose);
}
