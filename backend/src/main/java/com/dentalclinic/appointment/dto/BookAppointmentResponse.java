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
        UUID previousAppointmentId,
        OffsetDateTime scheduledStartAt,
        OffsetDateTime scheduledEndAt,
        AppointmentStatus status,
        Instant createdAt
) {
    public BookAppointmentResponse(
            UUID id,
            UUID patientId,
            UUID doctorId,
            UUID serviceId,
            OffsetDateTime scheduledStartAt,
            OffsetDateTime scheduledEndAt,
            AppointmentStatus status,
            Instant createdAt
    ) {
        this(
                id,
                patientId,
                doctorId,
                serviceId,
                null,
                scheduledStartAt,
                scheduledEndAt,
                status,
                createdAt
        );
    }
}
