package service.login;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    private boolean isCredentialsConfigured() {
        return mailUsername != null && !mailUsername.isBlank() && !mailUsername.contains("dien_email_gmail")
                && mailPassword != null && !mailPassword.isBlank() && !mailPassword.contains("dien_mat_khau");
    }

    public void sendEmail(String to, String code) {
        if (!isCredentialsConfigured()) {
            throw new IllegalStateException("Vui lòng mở file src/main/resources/application.properties, điền Gmail và Mật khẩu ứng dụng (16 ký tự) vào dòng spring.mail.username và spring.mail.password để gửi email thật.");
        }

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(to);
        mail.setSubject("[FreshFruit] Mã xác minh tài khoản của bạn");
        mail.setText("Xin chào,\n\nMã xác minh OTP của bạn là: " + code + "\nMã có hiệu lực trong vòng 5 phút.\n\nVui lòng không chia sẻ mã này cho bất kỳ ai.\n\nFreshFruit - Tươi ngon mỗi ngày.");
        mail.setFrom(mailUsername);
        mailSender.send(mail);
    }

    public void sendPasswordResetEmail(String to, String code) {
        if (!isCredentialsConfigured()) {
            throw new IllegalStateException("Vui lòng mở file src/main/resources/application.properties, điền Gmail và Mật khẩu ứng dụng (16 ký tự) vào dòng spring.mail.username và spring.mail.password để gửi email thật.");
        }

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(to);
        mail.setSubject("[FreshFruit] Mã xác minh đặt lại mật khẩu");
        mail.setText("Xin chào,\n\nMã OTP đặt lại mật khẩu của bạn là: " + code + "\nMã có hiệu lực trong vòng 5 phút.\n\nVui lòng không chia sẻ mã này cho bất kỳ ai.\n\nFreshFruit - Tươi ngon mỗi ngày.");
        mail.setFrom(mailUsername);
        mailSender.send(mail);
    }
}
