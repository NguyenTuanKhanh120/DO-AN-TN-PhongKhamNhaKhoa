package com.dentalclinic.schedule;

import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.service.SchedulingPolicy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchedulingPolicyTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate APPOINTMENT_DATE = LocalDate.of(2026, 11, 10);

    @Test
    void shouldDefineTwoEstimatedCasesForMorning() {
        SchedulingPolicy policy = policyAt("2026-11-09T20:00:00Z");

        assertThat(policy.sessionStart(ScheduleSession.MORNING)).isEqualTo(LocalTime.of(8, 0));
        assertThat(policy.sessionEnd(ScheduleSession.MORNING)).isEqualTo(LocalTime.of(11, 0));
        assertThat(policy.estimatedStartTime(ScheduleSession.MORNING, 1))
                .isEqualTo(LocalTime.of(8, 0));
        assertThat(policy.estimatedStartTime(ScheduleSession.MORNING, 2))
                .isEqualTo(LocalTime.of(9, 30));
    }

    @Test
    void shouldDefineTwoEstimatedCasesForAfternoon() {
        SchedulingPolicy policy = policyAt("2026-11-09T20:00:00Z");

        assertThat(policy.sessionStart(ScheduleSession.AFTERNOON)).isEqualTo(LocalTime.of(14, 0));
        assertThat(policy.sessionEnd(ScheduleSession.AFTERNOON)).isEqualTo(LocalTime.of(17, 0));
        assertThat(policy.estimatedStartTime(ScheduleSession.AFTERNOON, 1))
                .isEqualTo(LocalTime.of(14, 0));
        assertThat(policy.estimatedStartTime(ScheduleSession.AFTERNOON, 2))
                .isEqualTo(LocalTime.of(15, 30));
    }

    @Test
    void shouldDeriveEstimatedStartInClinicTimezone() {
        SchedulingPolicy policy = policyAt("2026-11-09T20:00:00Z");

        assertThat(policy.estimatedStartAt(APPOINTMENT_DATE, ScheduleSession.MORNING, 2))
                .isEqualTo(Instant.parse("2026-11-10T02:30:00Z"));
        assertThat(policy.estimatedStartAt(APPOINTMENT_DATE, ScheduleSession.AFTERNOON, 2))
                .isEqualTo(Instant.parse("2026-11-10T08:30:00Z"));
    }

    @Test
    void shouldResolvePublishedEstimatedStartToSessionAndQueue() {
        SchedulingPolicy policy = policyAt("2026-11-09T20:00:00Z");

        SchedulingPolicy.ScheduledCase scheduledCase = policy.resolveEstimatedStartAt(
                Instant.parse("2026-11-10T08:30:00Z")
        );

        assertThat(scheduledCase.appointmentDate()).isEqualTo(APPOINTMENT_DATE);
        assertThat(scheduledCase.session()).isEqualTo(ScheduleSession.AFTERNOON);
        assertThat(scheduledCase.queueNumber()).isEqualTo(2);
        assertThat(scheduledCase.estimatedStartAt())
                .isEqualTo(Instant.parse("2026-11-10T08:30:00Z"));
    }

    @Test
    void shouldResolveApiCaseKeyToEstimatedStart() {
        SchedulingPolicy policy = policyAt("2026-11-09T20:00:00Z");

        SchedulingPolicy.ScheduledCase scheduledCase = policy.resolveCase(
                APPOINTMENT_DATE,
                ScheduleSession.MORNING,
                2
        );

        assertThat(scheduledCase.appointmentDate()).isEqualTo(APPOINTMENT_DATE);
        assertThat(scheduledCase.session()).isEqualTo(ScheduleSession.MORNING);
        assertThat(scheduledCase.queueNumber()).isEqualTo(2);
        assertThat(scheduledCase.estimatedStartAt())
                .isEqualTo(Instant.parse("2026-11-10T02:30:00Z"));
    }

    @Test
    void shouldRejectTimeThatIsNotAPublishedEstimatedStart() {
        SchedulingPolicy policy = policyAt("2026-11-09T20:00:00Z");

        assertThatThrownBy(() -> policy.resolveEstimatedStartAt(
                Instant.parse("2026-11-10T07:30:00Z")
        )).isInstanceOf(BusinessException.class)
                .hasMessage("Thời gian dự kiến phải thuộc một trong các ca khám được công bố");
    }

    @Test
    void shouldAllowPatientActionAt0459AndRejectAt0500() {
        SchedulingPolicy beforeCutoff = policyAt("2026-11-09T21:59:00Z");
        SchedulingPolicy atCutoff = policyAt("2026-11-09T22:00:00Z");

        assertThat(beforeCutoff.patientDailyCutoffAt(APPOINTMENT_DATE))
                .isEqualTo(Instant.parse("2026-11-09T22:00:00Z"));
        assertThat(beforeCutoff.isBeforePatientDailyCutoff(APPOINTMENT_DATE)).isTrue();
        assertThat(atCutoff.isBeforePatientDailyCutoff(APPOINTMENT_DATE)).isFalse();
    }

    @Test
    void shouldRejectQueueOutsideOneAndTwo() {
        SchedulingPolicy policy = policyAt("2026-11-09T20:00:00Z");

        assertThatThrownBy(() -> policy.estimatedStartTime(ScheduleSession.MORNING, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Số ca chỉ được là 1 hoặc 2");
        assertThatThrownBy(() -> policy.estimatedStartTime(ScheduleSession.AFTERNOON, 3))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Số ca chỉ được là 1 hoặc 2");
    }

    private SchedulingPolicy policyAt(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), CLINIC_ZONE);
        return new SchedulingPolicy(new ClinicTimeProvider(clock));
    }
}
