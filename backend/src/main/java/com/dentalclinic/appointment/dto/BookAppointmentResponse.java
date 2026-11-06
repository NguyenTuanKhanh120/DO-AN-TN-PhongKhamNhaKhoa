package com.dentalclinic.appointment.dto;

import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.schedule.entity.ScheduleSession;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BookAppointmentResponse(
        UUID id,
        UUID patientId,
        UUID doctorId,
        UUID serviceId,
        UUID previousAppointmentId,
        LocalDate appointmentDate,
        ScheduleSession session,
        int queueNumber,
        LocalTime sessionStartTime,
        LocalTime sessionEndTime,
        LocalTime estimatedStartTime,
        OffsetDateTime estimatedStartAt,
        AppointmentStatus status,
        Instant createdAt
) {
}
