package service.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Dịch vụ cấu hình tỷ lệ phí sàn (commission) cho Admin.
 *
 * Lưu ý: schema gốc của nhóm đã có `shop_monthly_fees.fee_rate` nhưng hardcode 0.05.
 * Dịch vụ này tạo bảng `platform_config` để lưu giá trị cấu hình mới,
 * và sử dụng nó khi cần tính phí thực tế.
 */
@Service
public class AdminCommissionService {

    private final JdbcTemplate jdbc;

    public AdminCommissionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        ensureConfigTable();
    }

    private void ensureConfigTable() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS platform_config (
                    config_key VARCHAR(50) PRIMARY KEY,
                    config_value VARCHAR(200) NOT NULL,
                    description VARCHAR(500),
                    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                    updated_by BIGINT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    @Transactional(readOnly = true)
    public BigDecimal getCurrentCommissionRate() {
        List<BigDecimal> vals = jdbc.queryForList(
                "SELECT config_value FROM platform_config WHERE config_key='commission_rate'",
                BigDecimal.class);
        if (!vals.isEmpty()) return vals.get(0);
        return new BigDecimal("0.0500");
    }

    @Transactional
    public void setCommissionRate(BigDecimal rate, Long updatedBy) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(new BigDecimal("0.15")) > 0) {
            throw new IllegalArgumentException("Tỷ lệ phí sàn phải từ 0 đến 15%.");
        }
        String sql = "INSERT INTO platform_config (config_key, config_value, description, updated_by) " +
                "VALUES ('commission_rate', ?, 'Tỷ lệ phí sàn (0-100%)', ?) " +
                "ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), updated_by = VALUES(updated_by)";
        jdbc.update(sql, rate.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString(), updatedBy);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCommissionStats() {
        BigDecimal rate = getCurrentCommissionRate();
        Long totalFees = jdbc.queryForObject("SELECT COALESCE(SUM(fee_amount),0) FROM shop_monthly_fees", Long.class);
        Long paidFees = jdbc.queryForObject("SELECT COALESCE(SUM(fee_amount),0) FROM shop_monthly_fees WHERE status='PAID'", Long.class);
        Long unpaidFees = jdbc.queryForObject("SELECT COALESCE(SUM(fee_amount),0) FROM shop_monthly_fees WHERE status='UNPAID'", Long.class);
        return Map.of(
                "rate", rate,
                "totalFees", totalFees,
                "paidFees", paidFees,
                "unpaidFees", unpaidFees
        );
    }
}
