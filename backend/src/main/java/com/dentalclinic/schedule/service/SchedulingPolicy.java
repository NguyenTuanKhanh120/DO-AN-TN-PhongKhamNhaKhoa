package com.dentalclinic.schedule.service;

import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.schedule.entity.ScheduleSession;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;

@Component
public class SchedulingPolicy {

    public static final int MONDAY = 1;
    public static final int SATURDAY = 6;
    public static final int MIN_QUEUE_NUMBER = 1;
    public static final int MAX_QUEUE_NUMBER = 2;

    private static final LocalTime MORNING_START = LocalTime.of(8, 0);
    private static final LocalTime MORNING_END = LocalTime.of(11, 0);
    private static final LocalTime MORNING_QUEUE_1_ESTIMATED_START = LocalTime.of(8, 0);
    private static final LocalTime MORNING_QUEUE_2_ESTIMATED_START = LocalTime.of(9, 30);

    private static final LocalTime AFTERNOON_START = LocalTime.of(14, 0);
    private static final LocalTime AFTERNOON_END = LocalTime.of(17, 0);
    private static final LocalTime AFTERNOON_QUEUE_1_ESTIMATED_START = LocalTime.of(14, 0);
    private static final LocalTime AFTERNOON_QUEUE_2_ESTIMATED_START = LocalTime.of(15, 30);

    private static final LocalTime PATIENT_DAILY_CUTOFF = LocalTime.of(5, 0);

    private final ClinicTimeProvider clinicTimeProvider;

    public SchedulingPolicy(ClinicTimeProvider clinicTimeProvider) {
        this.clinicTimeProvider = clinicTimeProvider;
    }

    public LocalTime sessionStart(ScheduleSession session) {
        return switch (requireSession(session)) {
            case MORNING -> MORNING_START;
            case AFTERNOON -> AFTERNOON_START;
        };
    }

    public LocalTime sessionEnd(ScheduleSession session) {
        return switch (requireSession(session)) {
            case MORNING -> MORNING_END;
            case AFTERNOON -> AFTERNOON_END;
        };
    }

    public LocalTime estimatedStartTime(ScheduleSession session, int queueNumber) {
        requireQueueNumber(queueNumber);
        return switch (requireSession(session)) {
            case MORNING -> queueNumber == MIN_QUEUE_NUMBER
                    ? MORNING_QUEUE_1_ESTIMATED_START
                    : MORNING_QUEUE_2_ESTIMATED_START;
            case AFTERNOON -> queueNumber == MIN_QUEUE_NUMBER
                    ? AFTERNOON_QUEUE_1_ESTIMATED_START
                    : AFTERNOON_QUEUE_2_ESTIMATED_START;
        };
    }

    public Instant estimatedStartAt(
            LocalDate appointmentDate,
            ScheduleSession session,
            int queueNumber
    ) {
        if (appointmentDate == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Ngày khám không được để trống");
        }
        return clinicTimeProvider.toInstant(
                appointmentDate,
                estimatedStartTime(session, queueNumber)
        );
    }

    public ScheduledCase resolveCase(
            LocalDate appointmentDate,
            ScheduleSession session,
            int queueNumber
    ) {
        return new ScheduledCase(
                appointmentDate,
                requireSession(session),
                queueNumber,
                estimatedStartAt(appointmentDate, session, queueNumber)
        );
    }

    public ScheduledCase resolveEstimatedStartAt(Instant estimatedStartAt) {
        if (estimatedStartAt == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Thời gian dự kiến không được để trống"
            );
        }
        LocalDate appointmentDate = estimatedStartAt
                .atZone(clinicTimeProvider.zoneId())
                .toLocalDate();
        return Arrays.stream(ScheduleSession.values())
                .flatMap(session -> java.util.stream.IntStream.rangeClosed(
                                MIN_QUEUE_NUMBER,
                                MAX_QUEUE_NUMBER
                        )
                        .mapToObj(queueNumber -> new ScheduledCase(
                                appointmentDate,
                                session,
                                queueNumber,
                                estimatedStartAt(appointmentDate, session, queueNumber)
                        )))
                .filter(candidate -> candidate.estimatedStartAt().equals(estimatedStartAt))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Thời gian dự kiến phải thuộc một trong các ca khám được công bố"
                ));
    }

    public Instant patientDailyCutoffAt(LocalDate appointmentDate) {
        if (appointmentDate == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Ngày khám không được để trống");
        }
        return clinicTimeProvider.toInstant(appointmentDate, PATIENT_DAILY_CUTOFF);
    }

    public boolean isBeforePatientDailyCutoff(LocalDate appointmentDate) {
        return clinicTimeProvider.now().isBefore(patientDailyCutoffAt(appointmentDate));
    }

    private ScheduleSession requireSession(ScheduleSession session) {
        if (session == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Buổi khám không được để trống");
        }
        return session;
    }

    private void requireQueueNumber(int queueNumber) {
        if (queueNumber < MIN_QUEUE_NUMBER || queueNumber > MAX_QUEUE_NUMBER) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Số ca chỉ được là 1 hoặc 2");
        }
    }

    public record ScheduledCase(
            LocalDate appointmentDate,
            ScheduleSession session,
            int queueNumber,
            Instant estimatedStartAt
    ) {
    }
}
