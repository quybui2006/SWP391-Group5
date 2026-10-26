package service.vnpay;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class VNPayService {

    @Value("${vnpay.tmn-code:}")
    private String vnpTmnCode;

    @Value("${vnpay.hash-secret:}")
    private String vnpHashSecret;

    @Value("${vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String vnpPayUrl;

    @Value("${vnpay.return-url:http://localhost:8080/checkout/vnpay-return}")
    private String vnpReturnUrl;

    @Value("${vnpay.api-url:https://sandbox.vnpayment.vn/merchant_webapi/api/transaction}")
    private String vnpApiUrl;

    private static final ZoneId VNPAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNP_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public String createPaymentUrl(String checkoutCode, BigDecimal totalAmount, HttpServletRequest request) {
        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", "2.1.0");
        vnpParams.put("vnp_Command", "pay");
        vnpParams.put("vnp_TmnCode", vnpTmnCode);

        // VNPay yêu cầu số tiền nhân 100 để triệt tiêu phần thập phân
        long amount = totalAmount.multiply(BigDecimal.valueOf(100)).longValue();
        vnpParams.put("vnp_Amount", String.valueOf(amount));
        vnpParams.put("vnp_CurrCode", "VND");

        vnpParams.put("vnp_TxnRef", checkoutCode); // Dùng mã CK-... làm mã tham chiếu
        vnpParams.put("vnp_OrderInfo", "Thanh toan don hang FreshFruit " + checkoutCode);
        vnpParams.put("vnp_OrderType", "other");
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", vnpReturnUrl);
        vnpParams.put("vnp_IpAddr", getClientIp(request));

        LocalDateTime now = LocalDateTime.now(VNPAY_ZONE);
        vnpParams.put("vnp_CreateDate", now.format(VNP_DATE_FMT));
        vnpParams.put("vnp_ExpireDate", now.plusMinutes(15).format(VNP_DATE_FMT));

        // Sắp xếp các tham số theo thứ tự alphabet
        List<String> fieldNames = new ArrayList<>(vnpParams.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = vnpParams.get(fieldName);
            if ((fieldValue != null) && (!fieldValue.isEmpty())) {
                // Build hash data
                hashData.append(fieldName);
                hashData.append('=');
                hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                // Build query
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII));
                query.append('=');
                query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }

        String vnpSecureHash = hmacSHA512(vnpHashSecret, hashData.toString());
        query.append("&vnp_SecureHash=").append(vnpSecureHash);

        return vnpPayUrl + "?" + query;
    }

    public boolean verifyCallback(Map<String, String> fields) {
        String vnpSecureHash = fields.get("vnp_SecureHash");
        fields.remove("vnp_SecureHashType");
        fields.remove("vnp_SecureHash");

        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = fields.get(fieldName);
            if ((fieldValue != null) && (!fieldValue.isEmpty())) {
                hashData.append(fieldName);
                hashData.append("=");
                hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                if (itr.hasNext()) {
                    hashData.append("&");
                }
            }
        }
        String calculatedHash = hmacSHA512(vnpHashSecret, hashData.toString());
        return calculatedHash.equalsIgnoreCase(vnpSecureHash);
    }

    /**
     * Gọi API Hoàn tiền (Refund) của VNPAY Sandbox
     */
    public RefundResult refundTransaction(String txnRef,
                                          String transactionNo,
                                          BigDecimal refundAmount,
                                          boolean isFullRefund,
                                          String transactionDate,
                                          String createBy,
                                          String ipAddr,
                                          String orderInfo) {
        try {
            String vnpRequestId = UUID.randomUUID().toString().replace("-", "");
            String vnpVersion = "2.1.0";
            String vnpCommand = "refund";
            String vnpTransactionType = isFullRefund ? "02" : "03";
            String vnpAmount = String.valueOf(refundAmount.multiply(BigDecimal.valueOf(100)).longValue());
            String vnpCreateDate = LocalDateTime.now(VNPAY_ZONE).format(VNP_DATE_FMT);
            String safeTransNo = (transactionNo == null) ? "" : transactionNo;
            String safeTransDate = (transactionDate == null || transactionDate.isBlank()) ? vnpCreateDate : transactionDate;
            String safeCreateBy = (createBy == null || createBy.isBlank()) ? "System" : createBy;
            String safeIp = (ipAddr == null || ipAddr.isBlank()) ? "127.0.0.1" : ipAddr;
            String safeOrderInfo = (orderInfo == null || orderInfo.isBlank()) ? "Hoan tien don hang " + txnRef : orderInfo;

            if (vnpTmnCode == null || vnpTmnCode.isBlank() || vnpHashSecret == null || vnpHashSecret.isBlank()) {
                return new RefundResult(true, "00", "Hoàn tiền Sandbox nội bộ thành công", "LOCAL-REF-" + System.currentTimeMillis());
            }

            // Chuỗi dữ liệu tạo Checksum theo đúng chuẩn VNPAY Refund API 2.1.0
            String hashData = String.join("|",
                    vnpRequestId,
                    vnpVersion,
                    vnpCommand,
                    vnpTmnCode,
                    vnpTransactionType,
                    txnRef,
                    vnpAmount,
                    safeTransNo,
                    safeTransDate,
                    safeCreateBy,
                    vnpCreateDate,
                    safeIp,
                    safeOrderInfo
            );

            String vnpSecureHash = hmacSHA512(vnpHashSecret, hashData);

            // Tạo JSON payload thuần Java (không phụ thuộc Jackson)
            String jsonPayload = "{"
                    + "\"vnp_RequestId\":\"" + escapeJson(vnpRequestId) + "\","
                    + "\"vnp_Version\":\"" + escapeJson(vnpVersion) + "\","
                    + "\"vnp_Command\":\"" + escapeJson(vnpCommand) + "\","
                    + "\"vnp_TmnCode\":\"" + escapeJson(vnpTmnCode) + "\","
                    + "\"vnp_TransactionType\":\"" + escapeJson(vnpTransactionType) + "\","
                    + "\"vnp_TxnRef\":\"" + escapeJson(txnRef) + "\","
                    + "\"vnp_Amount\":\"" + escapeJson(vnpAmount) + "\","
                    + "\"vnp_OrderInfo\":\"" + escapeJson(safeOrderInfo) + "\","
                    + (safeTransNo.isEmpty() ? "" : "\"vnp_TransactionNo\":\"" + escapeJson(safeTransNo) + "\",")
                    + "\"vnp_TransactionDate\":\"" + escapeJson(safeTransDate) + "\","
                    + "\"vnp_CreateBy\":\"" + escapeJson(safeCreateBy) + "\","
                    + "\"vnp_CreateDate\":\"" + escapeJson(vnpCreateDate) + "\","
                    + "\"vnp_IpAddr\":\"" + escapeJson(safeIp) + "\","
                    + "\"vnp_SecureHash\":\"" + escapeJson(vnpSecureHash) + "\""
                    + "}";

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(vnpApiUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String respBody = response.body() != null ? response.body() : "";

            String responseCode = extractJsonField(respBody, "vnp_ResponseCode");
            String message = extractJsonField(respBody, "vnp_Message");
            String refundTransNo = extractJsonField(respBody, "vnp_TransactionNo");

            if (refundTransNo.isEmpty()) {
                refundTransNo = "VNP-REF-" + System.currentTimeMillis();
            }
            if (message.isEmpty()) {
                message = "Đã gửi yêu cầu hoàn tiền VNPAY";
            }

            boolean isSuccess = "00".equals(responseCode);
            return new RefundResult(isSuccess, responseCode, message, refundTransNo);
        } catch (Exception e) {
            return new RefundResult(false, "99", "Lỗi kết nối VNPAY Refund: " + e.getMessage(), "SANDBOX-REF-" + System.currentTimeMillis());
        }
    }

    private String hmacSHA512(String key, String data) {
        try {
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            byte[] hmacKeyBytes = key.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec secretKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA512");
            hmac512.init(secretKey);
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] result = hmac512.doFinal(dataBytes);
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Lỗi mã hóa HmacSHA512", ex);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-FORWARDED-FOR");
        if (ipAddress == null || ipAddress.isEmpty()) {
            ipAddress = request.getRemoteAddr();
        }
        return ipAddress;
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String extractJsonField(String json, String fieldName) {
        if (json == null || json.isEmpty()) return "";
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    public record RefundResult(boolean success, String responseCode, String message, String refundTransactionNo) {}
}