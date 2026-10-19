package org.example.swp391group5;

import dto.shop.VoucherRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class VoucherRequestValidationTests {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        if (factory != null) factory.close();
    }

    private VoucherRequest validPercentageVoucher() {
        VoucherRequest request = new VoucherRequest();
        request.setCode("FRESH20");
        request.setName("Ưu đãi khách hàng thân thiết");
        request.setDiscountType("PERCENTAGE");
        request.setDiscountValue(new BigDecimal("20"));
        request.setMaxDiscountAmount(new BigDecimal("50000"));
        request.setMinOrderValue(new BigDecimal("100000"));
        request.setMaxUsages(100);
        request.setIsPublic(true);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        request.setStartsAt(today.atTime(14, 30));
        request.setEndsAt(today.plusDays(1).atTime(18, 0));
        return request;
    }

    private Set<String> invalidProperties(VoucherRequest request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void acceptsValidPercentageVoucher() {
        assertTrue(invalidProperties(validPercentageVoucher()).isEmpty());
    }

    @Test
    void rejectsPercentageAbove100() {
        VoucherRequest request = validPercentageVoucher();
        request.setDiscountValue(new BigDecimal("101"));
        assertTrue(invalidProperties(request).contains("percentageValid"));
    }

    @Test
    void acceptsWholeVndAmountWithTrailingDecimalZeros() {
        VoucherRequest request = validPercentageVoucher();
        request.setDiscountValue(new BigDecimal("20.00"));
        assertTrue(invalidProperties(request).isEmpty());
    }

    @Test
    void rejectsFractionalVndAmount() {
        VoucherRequest request = validPercentageVoucher();
        request.setDiscountValue(new BigDecimal("20.5"));
        assertTrue(invalidProperties(request).contains("vndAmountsWholeNumbers"));
    }

    @Test
    void rejectsFixedAmountVoucherWithPercentageCap() {
        VoucherRequest request = validPercentageVoucher();
        request.setDiscountType("FIXED_AMOUNT");
        request.setDiscountValue(new BigDecimal("20000"));
        request.setMaxDiscountAmount(new BigDecimal("50000"));
        assertTrue(invalidProperties(request).contains("maxDiscountValid"));
    }

    @Test
    void rejectsEndTimeNotAfterStartTime() {
        VoucherRequest request = validPercentageVoucher();
        request.setEndsAt(request.getStartsAt());
        assertTrue(invalidProperties(request).contains("dateRangeValid"));
    }


    @Test
    void rejectsVoucherStartDateBeforeTodayInVietnam() {
        VoucherRequest request = validPercentageVoucher();
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(1);
        request.setStartsAt(yesterday.atTime(14, 30));
        request.setEndsAt(yesterday.plusDays(2).atTime(18, 0));
        assertTrue(invalidProperties(request).contains("startDateNotBeforeToday"));
    }

    @Test
    void acceptsVoucherStartDateInFuture() {
        VoucherRequest request = validPercentageVoucher();
        LocalDate futureDate = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(10);
        request.setStartsAt(futureDate.atTime(14, 30));
        request.setEndsAt(futureDate.plusDays(1).atTime(18, 0));
        assertFalse(invalidProperties(request).contains("startDateNotBeforeToday"));
        assertFalse(invalidProperties(request).contains("dateRangeValid"));
    }

    @Test
    void rejectsZeroUsageLimit() {
        VoucherRequest request = validPercentageVoucher();
        request.setMaxUsages(0);
        assertTrue(invalidProperties(request).contains("maxUsages"));
    }
}
