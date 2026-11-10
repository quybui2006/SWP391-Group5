package service.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Chốt phí sàn của tháng liền trước vào đầu tháng mới. Việc chốt nằm ở ứng
 * dụng để vẫn hoạt động với các database đã được baseline trước khi procedure
 * usp_generate_monthly_fees được thêm vào migration.
 */
@Component
public class MonthlyFeeScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonthlyFeeScheduler.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final JdbcTemplate jdbc;

    public MonthlyFeeScheduler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Chạy lúc 00:10 ngày đầu tiên mỗi tháng, giờ Việt Nam. */
    @Scheduled(cron = "${fees.monthly-generation-cron:0 10 0 1 * *}", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void generatePreviousMonthFees() {
        LocalDate previousMonth = LocalDate.now(BUSINESS_ZONE).minusMonths(1).withDayOfMonth(1);
        try {
            createMonthlyFees(previousMonth);
            log.info("Đã chốt phí sàn cho tháng {}.", previousMonth);
        } catch (DataAccessException ex) {
            // Không để lỗi cơ sở dữ liệu làm dừng scheduler của các tính năng khác.
            log.error("Không thể chốt phí sàn cho tháng {}.", previousMonth, ex);
        }
    }

    private void createMonthlyFees(LocalDate revenueMonth) {
        LocalDate nextMonth = revenueMonth.plusMonths(1);
        // completed_at được lưu theo UTC; khoảng chốt được tính theo giờ Việt Nam.
        java.sql.Timestamp from = java.sql.Timestamp.valueOf(revenueMonth.atStartOfDay(BUSINESS_ZONE).toLocalDateTime().minusHours(7));
        java.sql.Timestamp to = java.sql.Timestamp.valueOf(nextMonth.atStartOfDay(BUSINESS_ZONE).toLocalDateTime().minusHours(7));
        java.sql.Timestamp dueAt = java.sql.Timestamp.valueOf(revenueMonth.plusMonths(1).withDayOfMonth(8).atStartOfDay(BUSINESS_ZONE).toLocalDateTime().minusHours(7));

        jdbc.update("""
                INSERT INTO shop_monthly_fees
                    (shop_id, revenue_month, revenue_amount, fee_rate, fee_amount, due_at, status, paid_at)
                SELECT o.shop_id, ?, SUM(o.shop_revenue_amount), 0.0500,
                       ROUND(SUM(o.shop_revenue_amount) * 0.05, 0),
                       CAST(? AS DATETIME),
                       CASE WHEN ROUND(SUM(o.shop_revenue_amount) * 0.05, 0) = 0 THEN 'PAID' ELSE 'UNPAID' END,
                       CASE WHEN ROUND(SUM(o.shop_revenue_amount) * 0.05, 0) = 0 THEN CURRENT_TIMESTAMP(3) ELSE NULL END
                FROM orders o
                WHERE o.status = 'COMPLETED' AND o.payment_status = 'PAID'
                    AND o.completed_at >= ? AND o.completed_at < ?
                GROUP BY o.shop_id
                ON DUPLICATE KEY UPDATE shop_id = VALUES(shop_id)
                """, Date.valueOf(revenueMonth), dueAt, from, to);

        jdbc.update("""
                UPDATE orders o
                JOIN shop_monthly_fees f ON f.shop_id = o.shop_id AND f.revenue_month = ?
                SET o.monthly_fee_id = f.id
                WHERE o.status = 'COMPLETED' AND o.payment_status = 'PAID'
                    AND o.completed_at >= ? AND o.completed_at < ? AND o.monthly_fee_id IS NULL
                """, Date.valueOf(revenueMonth), from, to);
    }

    /**
     * Bù kỳ liền trước nếu ứng dụng được triển khai/khởi động sau lịch đầu tháng.
     * Procedure là idempotent nên lần chạy định kỳ sau đó sẽ không tạo trùng phí.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void generatePreviousMonthFeesAfterStartup() {
        generatePreviousMonthFees();
    }
}
