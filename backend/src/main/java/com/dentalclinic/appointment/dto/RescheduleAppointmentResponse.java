package com.dentalclinic.appointment.dto;

import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.schedule.entity.ScheduleSession;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RescheduleAppointmentResponse(
        UUID id,
        AppointmentStatus status,
        LocalDate oldAppointmentDate,
        ScheduleSession oldSession,
        Integer oldQueueNumber,
        OffsetDateTime oldEstimatedStartAt,
        LocalDate appointmentDate,
        ScheduleSession session,
        int queueNumber,
        LocalTime sessionStartTime,
        LocalTime sessionEndTime,
        LocalTime estimatedStartTime,
        OffsetDateTime estimatedStartAt,
        String reason,
        OffsetDateTime changedAt
) {
}
