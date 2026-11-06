package com.dentalclinic.appointment.dto;

import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.schedule.entity.ScheduleSession;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record NoShowAppointmentResponse(
        UUID id,
        AppointmentStatus status,
        LocalDate appointmentDate,
        ScheduleSession session,
        Integer queueNumber,
        OffsetDateTime estimatedStartAt,
        OffsetDateTime noShowAt
) {
}
