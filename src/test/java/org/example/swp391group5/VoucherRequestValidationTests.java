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
        request.setStartsAt(LocalDateTime.of(2026, 10, 19, 14, 30));
        request.setEndsAt(LocalDateTime.of(2026, 10, 20, 18, 0));
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
    void rejectsZeroUsageLimit() {
        VoucherRequest request = validPercentageVoucher();
        request.setMaxUsages(0);
        assertTrue(invalidProperties(request).contains("maxUsages"));
    }
}
