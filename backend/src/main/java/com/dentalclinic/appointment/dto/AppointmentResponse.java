package com.dentalclinic.appointment.dto;

import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.schedule.entity.ScheduleSession;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentResponse(
        UUID id,
        UUID patientId,
        String patientFullName,
        String patientPhone,
        UUID doctorId,
        String doctorFullName,
        String doctorSpecialization,
        UUID serviceId,
        String serviceName,
        UUID previousAppointmentId,
        LocalDate appointmentDate,
        ScheduleSession session,
        Integer queueNumber,
        LocalTime sessionStartTime,
        LocalTime sessionEndTime,
        LocalTime estimatedStartTime,
        OffsetDateTime estimatedStartAt,
        AppointmentStatus status,
        OffsetDateTime checkedInAt,
        OffsetDateTime actualStartAt,
        OffsetDateTime actualEndAt,
        String cancellationReason,
        String cancellationSource,
        Instant cancelledAt,
        Instant createdAt,
        Instant updatedAt
) {
}
