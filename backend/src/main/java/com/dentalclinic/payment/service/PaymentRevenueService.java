package com.dentalclinic.payment.service;

import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.payment.dto.RevenueSummaryResponse;
import com.dentalclinic.payment.entity.PaymentStatus;
import com.dentalclinic.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;

@Service
public class PaymentRevenueService {

    private final PaymentRepository paymentRepository;
    private final ClinicTimeProvider clinicTimeProvider;

    public PaymentRevenueService(
            PaymentRepository paymentRepository,
            ClinicTimeProvider clinicTimeProvider
    ) {
        this.paymentRepository = paymentRepository;
        this.clinicTimeProvider = clinicTimeProvider;
    }

    @Transactional(readOnly = true)
    public RevenueSummaryResponse getRevenue(
            LocalDate fromDate,
            LocalDate toDate,
            YearMonth month
    ) {
        if ((fromDate == null) != (toDate == null)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Phải cung cấp đồng thời từ ngày và đến ngày"
            );
        }
        LocalDate today = clinicTimeProvider.today();
        LocalDate resolvedFrom = fromDate == null ? today : fromDate;
        LocalDate resolvedTo = toDate == null ? today : toDate;
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Từ ngày không được sau đến ngày"
            );
        }
        YearMonth resolvedMonth = month == null ? YearMonth.from(today) : month;

        BigDecimal todayRevenue = paidRevenue(today, today);
        BigDecimal rangeRevenue = paidRevenue(resolvedFrom, resolvedTo);
        BigDecimal monthlyRevenue = paidRevenue(
                resolvedMonth.atDay(1),
                resolvedMonth.atEndOfMonth()
        );
        return new RevenueSummaryResponse(
                todayRevenue,
                resolvedFrom,
                resolvedTo,
                rangeRevenue,
                resolvedMonth,
                monthlyRevenue,
                paymentRepository.countByStatus(PaymentStatus.PAID),
                paymentRepository.countByStatus(PaymentStatus.UNPAID),
                money(paymentRepository.sumAmountByStatus(PaymentStatus.UNPAID))
        );
    }

    private BigDecimal paidRevenue(LocalDate fromDate, LocalDate toDate) {
        Instant rangeStart = clinicTimeProvider.toInstant(fromDate, LocalTime.MIDNIGHT);
        Instant rangeEnd;
        try {
            rangeEnd = clinicTimeProvider.toInstant(
                    toDate.plusDays(1),
                    LocalTime.MIDNIGHT
            );
        } catch (DateTimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Khoảng ngày không hợp lệ");
        }
        return money(paymentRepository.sumPaidAmountInRange(
                PaymentStatus.PAID,
                rangeStart,
                rangeEnd
        ));
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.UNNECESSARY);
    }
}
