package com.dentalclinic.appointment;

import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.schedule.entity.ScheduleSession;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class AppointmentQueueFixtureSupport {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalTime MORNING_SECOND_CASE = LocalTime.of(9, 30);
    private static final LocalTime AFTERNOON_SECOND_CASE = LocalTime.of(15, 30);

    private AppointmentQueueFixtureSupport() {
    }

    public static void applyRequiredQueueModel(
            Appointment appointment,
            Instant estimatedStartAt
    ) {
        ZonedDateTime clinicStart = estimatedStartAt.atZone(CLINIC_ZONE);
        LocalTime localTime = clinicStart.toLocalTime();
        ScheduleSession session = localTime.isBefore(LocalTime.NOON)
                ? ScheduleSession.MORNING
                : ScheduleSession.AFTERNOON;
        LocalTime secondCaseThreshold = session == ScheduleSession.MORNING
                ? MORNING_SECOND_CASE
                : AFTERNOON_SECOND_CASE;

        appointment.setAppointmentDate(clinicStart.toLocalDate());
        appointment.setSession(session);
        appointment.setQueueNumber((short) (localTime.isBefore(secondCaseThreshold) ? 1 : 2));
    }
}
