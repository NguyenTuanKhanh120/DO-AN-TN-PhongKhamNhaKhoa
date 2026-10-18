package com.dentalclinic.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

public record RevenueSummaryResponse(
        BigDecimal todayRevenue,
        LocalDate rangeFrom,
        LocalDate rangeTo,
        BigDecimal rangeRevenue,
        YearMonth month,
        BigDecimal monthlyRevenue,
        long totalPaidPayments,
        long totalUnpaidPayments,
        BigDecimal totalUnpaidAmount
) {
}
