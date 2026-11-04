package com.dentalclinic.ai.conversation;

import com.dentalclinic.ai.conversation.model.ChatDateRange;
import com.dentalclinic.ai.conversation.service.VietnameseRelativeDateResolver;
import com.dentalclinic.common.util.ClinicTimeProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class VietnameseRelativeDateResolverTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-29T03:00:00Z"),
            CLINIC_ZONE
    );
    private final VietnameseRelativeDateResolver resolver = new VietnameseRelativeDateResolver(
            new ClinicTimeProvider(CLOCK)
    );

    @Test
    void shouldResolveVietnameseRelativeDaysInClinicTimezone() {
        assertThat(resolver.resolve("hôm nay"))
                .contains(ChatDateRange.single(LocalDate.of(2026, 10, 29)));
        assertThat(resolver.resolve("ngày mai"))
                .contains(ChatDateRange.single(LocalDate.of(2026, 10, 30)));
        assertThat(resolver.resolve("ngày kia"))
                .contains(ChatDateRange.single(LocalDate.of(2026, 10, 31)));
    }

    @Test
    void shouldResolveNextWeekAndWeekendRanges() {
        assertThat(resolver.resolve("tuần sau")).contains(new ChatDateRange(
                LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 8)
        ));
        assertThat(resolver.resolve("cuối tuần sau")).contains(new ChatDateRange(
                LocalDate.of(2026, 11, 7),
                LocalDate.of(2026, 11, 8)
        ));
    }

    @Test
    void shouldResolveWeekdayWithAndWithoutWeekQualifier() {
        assertThat(resolver.resolve("thứ tư tuần sau"))
                .contains(ChatDateRange.single(LocalDate.of(2026, 11, 4)));
        assertThat(resolver.resolve("thứ hai"))
                .contains(ChatDateRange.single(LocalDate.of(2026, 11, 2)));
    }

    @Test
    void shouldResolveExplicitDatesAndUpcomingDayRange() {
        assertThat(resolver.resolve("31/10/2026"))
                .contains(ChatDateRange.single(LocalDate.of(2026, 10, 31)));
        assertThat(resolver.resolve("2026-11-01"))
                .contains(ChatDateRange.single(LocalDate.of(2026, 11, 1)));
        assertThat(resolver.resolve("3 ngày tới")).contains(new ChatDateRange(
                LocalDate.of(2026, 10, 29),
                LocalDate.of(2026, 10, 31)
        ));
    }

    @Test
    void shouldReturnEmptyForUnsupportedOrInvalidDate() {
        assertThat(resolver.resolve("ngày 31/02/2026")).isEmpty();
        assertThat(resolver.resolve("khi nào rảnh")).isEmpty();
        assertThat(resolver.resolve(null)).isEmpty();
    }
}
