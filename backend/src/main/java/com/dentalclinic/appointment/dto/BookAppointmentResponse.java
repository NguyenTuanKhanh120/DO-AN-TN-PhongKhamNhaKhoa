package com.dentalclinic.appointment.dto;

import com.dentalclinic.appointment.entity.AppointmentStatus;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BookAppointmentResponse(
        UUID id,
        UUID patientId,
        UUID doctorId,
        UUID serviceId,
        OffsetDateTime scheduledStartAt,
        OffsetDateTime scheduledEndAt,
        AppointmentStatus status,
        Instant createdAt
) {
}
