package com.dentalclinic.common;

import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.ForbiddenException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.common.util.ClinicTimeProvider;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommonModuleTest {

    @Test
    void shouldCreateSuccessfulApiResponse() {
        ApiResponse<String> response = ApiResponse.success("Đã xử lý", "payload");

        assertThat(response.success()).isTrue();
        assertThat(response.code()).isEqualTo("SUCCESS");
        assertThat(response.message()).isEqualTo("Đã xử lý");
        assertThat(response.data()).isEqualTo("payload");
        assertThat(response.errors()).isNull();
        assertThat(response.path()).isNull();
    }

    @Test
    void shouldCreateImmutablePageResponseFromSpringPage() {
        PageImpl<String> page = new PageImpl<>(
                List.of("a", "b"),
                PageRequest.of(1, 2),
                5
        );

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.first()).isFalse();
        assertThat(response.last()).isFalse();
        assertThat(response.hasNext()).isTrue();
        assertThat(response.hasPrevious()).isTrue();
    }

    @Test
    void shouldExposeExpectedErrorCodesFromExceptionHierarchy() {
        assertThat(new NotFoundException().getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(new ConflictException().getErrorCode()).isEqualTo(ErrorCode.RESOURCE_CONFLICT);
        assertThat(new ForbiddenException().getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED);

        BusinessException exception = new BusinessException(ErrorCode.INVALID_REQUEST, "Quy tắc không hợp lệ");
        assertThat(exception.getMessage()).isEqualTo("Quy tắc không hợp lệ");
    }

    @Test
    void shouldUseConfiguredClinicClockForAllConversions() {
        ZoneId clinicZone = ZoneId.of("Asia/Ho_Chi_Minh");
        Instant fixedInstant = Instant.parse("2026-10-08T10:15:30Z");
        ClinicTimeProvider timeProvider = new ClinicTimeProvider(Clock.fixed(fixedInstant, clinicZone));

        assertThat(timeProvider.now()).isEqualTo(fixedInstant);
        assertThat(timeProvider.nowAtClinic().toLocalTime()).isEqualTo(LocalTime.of(17, 15, 30));
        assertThat(timeProvider.today()).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(timeProvider.zoneId()).isEqualTo(clinicZone);
        assertThat(timeProvider.toInstant(LocalDate.of(2026, 10, 9), LocalTime.of(7, 0)))
                .isEqualTo(Instant.parse("2026-10-09T00:00:00Z"));
        assertThat(timeProvider.toClinicTime(fixedInstant).getZone()).isEqualTo(clinicZone);
    }
}
