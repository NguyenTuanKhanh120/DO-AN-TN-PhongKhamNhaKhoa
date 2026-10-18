package com.dentalclinic.payment;

import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.payment.dto.RevenueSummaryResponse;
import com.dentalclinic.payment.entity.PaymentStatus;
import com.dentalclinic.payment.repository.PaymentRepository;
import com.dentalclinic.payment.service.PaymentRevenueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRevenueServiceTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");

    @Mock
    private PaymentRepository paymentRepository;

    private PaymentRevenueService revenueService;

    @BeforeEach
    void setUp() {
        revenueService = new PaymentRevenueService(
                paymentRepository,
                new ClinicTimeProvider(Clock.fixed(NOW, CLINIC_ZONE))
        );
    }

    @Test
    void shouldCalculateRevenueOnlyFromPaidAtAndReportUnpaidAmount() {
        Instant todayStart = Instant.parse("2026-10-19T17:00:00Z");
        Instant todayEnd = Instant.parse("2026-10-20T17:00:00Z");
        Instant rangeStart = Instant.parse("2026-10-09T17:00:00Z");
        Instant rangeEnd = Instant.parse("2026-10-21T17:00:00Z");
        Instant monthStart = Instant.parse("2026-09-30T17:00:00Z");
        Instant monthEnd = Instant.parse("2026-10-31T17:00:00Z");
        when(paymentRepository.sumPaidAmountInRange(
                PaymentStatus.PAID, todayStart, todayEnd
        )).thenReturn(new BigDecimal("400000.00"));
        when(paymentRepository.sumPaidAmountInRange(
                PaymentStatus.PAID, rangeStart, rangeEnd
        )).thenReturn(new BigDecimal("900000.00"));
        when(paymentRepository.sumPaidAmountInRange(
                PaymentStatus.PAID, monthStart, monthEnd
        )).thenReturn(new BigDecimal("1500000.00"));
        when(paymentRepository.countByStatus(PaymentStatus.PAID)).thenReturn(8L);
        when(paymentRepository.countByStatus(PaymentStatus.UNPAID)).thenReturn(3L);
        when(paymentRepository.sumAmountByStatus(PaymentStatus.UNPAID))
                .thenReturn(new BigDecimal("650000.00"));

        RevenueSummaryResponse response = revenueService.getRevenue(
                LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 21),
                YearMonth.of(2026, 10)
        );

        assertThat(response.todayRevenue()).isEqualByComparingTo("400000.00");
        assertThat(response.rangeRevenue()).isEqualByComparingTo("900000.00");
        assertThat(response.monthlyRevenue()).isEqualByComparingTo("1500000.00");
        assertThat(response.totalPaidPayments()).isEqualTo(8);
        assertThat(response.totalUnpaidPayments()).isEqualTo(3);
        assertThat(response.totalUnpaidAmount()).isEqualByComparingTo("650000.00");
    }

    @Test
    void shouldDefaultRangeAndMonthToClinicToday() {
        when(paymentRepository.sumPaidAmountInRange(
                PaymentStatus.PAID,
                Instant.parse("2026-10-19T17:00:00Z"),
                Instant.parse("2026-10-20T17:00:00Z")
        )).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.sumPaidAmountInRange(
                PaymentStatus.PAID,
                Instant.parse("2026-09-30T17:00:00Z"),
                Instant.parse("2026-10-31T17:00:00Z")
        )).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.sumAmountByStatus(PaymentStatus.UNPAID))
                .thenReturn(BigDecimal.ZERO);

        RevenueSummaryResponse response = revenueService.getRevenue(null, null, null);

        assertThat(response.rangeFrom()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(response.rangeTo()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(response.month()).isEqualTo(YearMonth.of(2026, 10));
    }

    @Test
    void shouldRejectPartialOrReversedDateRange() {
        assertThatThrownBy(() -> revenueService.getRevenue(
                LocalDate.of(2026, 10, 20), null, null
        )).isInstanceOf(BusinessException.class)
                .hasMessage("Phải cung cấp đồng thời từ ngày và đến ngày");

        assertThatThrownBy(() -> revenueService.getRevenue(
                LocalDate.of(2026, 10, 21),
                LocalDate.of(2026, 10, 20),
                null
        )).isInstanceOf(BusinessException.class)
                .hasMessage("Từ ngày không được sau đến ngày");
    }
}
