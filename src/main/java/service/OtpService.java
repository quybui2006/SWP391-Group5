package service;

import entity.EmailOtp;
import entity.User;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import repository.EmailOtpRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class OtpService {
    private final EmailOtpRepository otps;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public OtpService(EmailOtpRepository otps, PasswordEncoder passwordEncoder) {
        this.otps = otps;
        this.passwordEncoder = passwordEncoder;
    }

    public String createRegistrationOtp(User user) {
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        EmailOtp otp = new EmailOtp();
        otp.setUser(user);
        otp.setEmail(user.getEmail());
        otp.setPurpose("REGISTER");
        otp.setOtpHash(passwordEncoder.encode(code));
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        otps.save(otp);
        return  code;
    }
    @Transactional
    public boolean verifyRegistrationOtp(String email,String code) {
        if(email==null || code==null) return false;
        EmailOtp otp=otps.findFirstByEmailIgnoreCaseAndPurposeOrderByIdDesc
                (email.trim(), "REGISTER").orElse(null);
        if(otp==null || otp.getUsedAt()!=null||
                !otp.getExpiresAt().isAfter(LocalDateTime.now())||otp.getFailedAttempts()>=5) {
            return false;
        }
        if(!passwordEncoder.matches(code, otp.getOtpHash())) {
            otp.setFailedAttempts(otp.getFailedAttempts() + 1);
            return false;
        }
        otp.setUsedAt(LocalDateTime.now());
        otp.getUser().setEmailVerifiedAt(LocalDateTime.now());
        return true;
    }

}
