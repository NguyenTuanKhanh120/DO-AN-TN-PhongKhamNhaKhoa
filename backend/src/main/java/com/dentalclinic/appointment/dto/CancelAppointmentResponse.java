package com.dentalclinic.appointment.dto;

import com.dentalclinic.appointment.entity.AppointmentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CancelAppointmentResponse(
        UUID id,
        AppointmentStatus status,
        OffsetDateTime scheduledStartAt,
        String reason,
        OffsetDateTime cancelledAt
) {
}
