package service.login;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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

    /**
     * Gửi email HTML xác nhận đơn hàng kèm link tra cứu cho khách vãng lai (Guest).
     */
    public void sendOrderConfirmationEmail(String toEmail, String recipientName, List<String> orderCodes,
                                           String fullAddress, String phone,
                                           BigDecimal totalAmount,
                                           List<Map<String, Object>> items,
                                           String trackingUrl) {
        if (!isCredentialsConfigured() || toEmail == null || toEmail.isBlank()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

                helper.setFrom(mailUsername);
                helper.setTo(toEmail);
                String codesStr = (orderCodes == null || orderCodes.isEmpty()) ? "" : String.join(", ", orderCodes);
                helper.setSubject("[FreshFruit] Xác nhận đặt hàng thành công #" + codesStr);

                StringBuilder itemsHtml = new StringBuilder();
                if (items != null) {
                    for (Map<String, Object> item : items) {
                        String prodName = String.valueOf(item.getOrDefault("product_name", "Sản phẩm"));
                        String varName = String.valueOf(item.getOrDefault("variant_name", ""));
                        String title = varName.isBlank() || varName.equals(prodName) ? prodName : prodName + " (" + varName + ")";
                        int qty = ((Number) item.getOrDefault("quantity", 1)).intValue();
                        BigDecimal price = item.get("price") instanceof BigDecimal p ? p : BigDecimal.ZERO;
                        BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(qty));

                        itemsHtml.append("<tr>")
                                .append("<td style=\"padding: 10px 12px; border-bottom: 1px solid #e5e7eb; font-size: 14px;\">").append(escapeHtml(title)).append("</td>")
                                .append("<td style=\"padding: 10px 12px; border-bottom: 1px solid #e5e7eb; font-size: 14px; text-align: center;\">").append(qty).append("</td>")
                                .append("<td style=\"padding: 10px 12px; border-bottom: 1px solid #e5e7eb; font-size: 14px; text-align: right;\">").append(formatMoney(price)).append("</td>")
                                .append("<td style=\"padding: 10px 12px; border-bottom: 1px solid #e5e7eb; font-size: 14px; text-align: right; font-weight: 600;\">").append(formatMoney(lineTotal)).append("</td>")
                                .append("</tr>");
                    }
                }

                String template = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                    </head>
                    <body style="margin: 0; padding: 0; background-color: #f4f6f8; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #1f2937;">
                        <table width="100%" cellpadding="0" cellspacing="0" style="background-color: #f4f6f8; padding: 30px 10px;">
                            <tr>
                                <td align="center">
                                    <table width="600" cellpadding="0" cellspacing="0" style="background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 16px rgba(0,0,0,0.06); max-width: 100%;">
                                        <!-- Header -->
                                        <tr>
                                            <td style="background: linear-gradient(135deg, #16803c 0%, #22c55e 100%); padding: 28px 30px; text-align: center; color: #ffffff;">
                                                <h1 style="margin: 0; font-size: 26px; font-weight: 800; letter-spacing: -0.5px;">FreshFruit</h1>
                                                <p style="margin: 6px 0 0; font-size: 14px; opacity: 0.9;">Tươi ngon mỗi ngày</p>
                                            </td>
                                        </tr>
                                        <!-- Body -->
                                        <tr>
                                            <td style="padding: 32px 30px;">
                                                <h2 style="margin: 0 0 12px; font-size: 20px; color: #111827;">Cảm ơn bạn đã đặt hàng!</h2>
                                                <p style="margin: 0 0 20px; font-size: 14.5px; line-height: 1.6; color: #4b5563;">
                                                    Xin chào <strong>{{recipientName}}</strong>, FreshFruit đã nhận được đơn đặt hàng của bạn và đang gửi thông báo tới cửa hàng để chuẩn bị sản phẩm.
                                                </p>

                                                <!-- Order Info Box -->
                                                <table width="100%" cellpadding="0" cellspacing="0" style="background-color: #f0fdf4; border: 1px solid #bbf7d0; border-radius: 8px; padding: 16px; margin-bottom: 24px;">
                                                    <tr>
                                                        <td style="font-size: 14px; line-height: 1.8;">
                                                            <div><strong>Mã đơn hàng:</strong> <span style="color: #16803c; font-weight: 700;">{{orderCodes}}</span></div>
                                                            <div><strong>Hình thức thanh toán:</strong> Thanh toán khi nhận hàng (COD)</div>
                                                            <div><strong>Người nhận:</strong> {{recipientName}} - {{recipientPhone}}</div>
                                                            <div><strong>Địa chỉ giao:</strong> {{fullAddress}}</div>
                                                        </td>
                                                    </tr>
                                                </table>

                                                <!-- Items Table -->
                                                <table width="100%" cellpadding="0" cellspacing="0" style="border-collapse: collapse; margin-bottom: 20px;">
                                                    <thead>
                                                        <tr style="background-color: #f9fafb;">
                                                            <th style="padding: 10px 12px; border-bottom: 2px solid #e5e7eb; font-size: 13px; text-align: left; color: #6b7280; font-weight: 600;">SẢN PHẨM</th>
                                                            <th style="padding: 10px 12px; border-bottom: 2px solid #e5e7eb; font-size: 13px; text-align: center; color: #6b7280; font-weight: 600;">SL</th>
                                                            <th style="padding: 10px 12px; border-bottom: 2px solid #e5e7eb; font-size: 13px; text-align: right; color: #6b7280; font-weight: 600;">ĐƠN GIÁ</th>
                                                            <th style="padding: 10px 12px; border-bottom: 2px solid #e5e7eb; font-size: 13px; text-align: right; color: #6b7280; font-weight: 600;">THÀNH TIỀN</th>
                                                        </tr>
                                                    </thead>
                                                    <tbody>
                                                        {{itemsHtml}}
                                                    </tbody>
                                                </table>

                                                <!-- Total -->
                                                <div style="text-align: right; padding: 12px 0; border-top: 2px solid #e5e7eb; margin-bottom: 26px;">
                                                    <span style="font-size: 15px; color: #4b5563; margin-right: 12px;">Tổng thanh toán:</span>
                                                    <span style="font-size: 22px; font-weight: 800; color: #16803c;">{{totalAmount}}</span>
                                                </div>

                                                <!-- CTA Button -->
                                                <div style="text-align: center; margin: 30px 0 16px;">
                                                    <a href="{{trackingUrl}}" style="display: inline-block; background: #16803c; color: #ffffff; text-decoration: none; padding: 14px 32px; border-radius: 8px; font-size: 15px; font-weight: 700; box-shadow: 0 4px 12px rgba(22, 128, 60, 0.25);">
                                                        Theo dõi tình trạng đơn hàng ➔
                                                    </a>
                                                </div>

                                                <p style="text-align: center; font-size: 13px; color: #9ca3af; margin: 14px 0 0;">
                                                    Bạn có thể dùng mã đơn hàng để tra cứu trạng thái đơn hàng bất kỳ lúc nào tại website FreshFruit.
                                                </p>
                                            </td>
                                        </tr>
                                        <!-- Footer -->
                                        <tr>
                                            <td style="background-color: #f9fafb; padding: 20px 30px; text-align: center; font-size: 12.5px; color: #9ca3af; border-top: 1px solid #e5e7eb;">
                                                FreshFruit © 2026 - Nền tảng phân phối trái cây tươi sạch mỗi ngày.
                                            </td>
                                        </tr>
                                    </table>
                                </td>
                            </tr>
                        </table>
                    </body>
                    </html>
                    """;

                String html = template
                        .replace("{{recipientName}}", escapeHtml(recipientName))
                        .replace("{{orderCodes}}", escapeHtml(codesStr))
                        .replace("{{recipientPhone}}", escapeHtml(phone))
                        .replace("{{fullAddress}}", escapeHtml(fullAddress))
                        .replace("{{itemsHtml}}", itemsHtml.toString())
                        .replace("{{totalAmount}}", formatMoney(totalAmount))
                        .replace("{{trackingUrl}}", trackingUrl);

                helper.setText(html, true);
                mailSender.send(mimeMessage);
            } catch (Exception e) {
                System.err.println("Lỗi khi gửi email xác nhận đơn hàng: " + e.getMessage());
            }
        });
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) return "0 ₫";
        return String.format("%,d ₫", amount.longValue()).replace(',', '.');
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
