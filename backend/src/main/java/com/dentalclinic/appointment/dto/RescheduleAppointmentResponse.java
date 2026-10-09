package com.dentalclinic.appointment.dto;

import com.dentalclinic.appointment.entity.AppointmentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RescheduleAppointmentResponse(
        UUID id,
        AppointmentStatus status,
        OffsetDateTime oldScheduledStartAt,
        OffsetDateTime scheduledStartAt,
        OffsetDateTime scheduledEndAt,
        String reason,
        OffsetDateTime changedAt
) {
}
