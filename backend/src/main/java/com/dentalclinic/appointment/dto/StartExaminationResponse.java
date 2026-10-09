package com.dentalclinic.appointment.dto;

import com.dentalclinic.appointment.entity.AppointmentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StartExaminationResponse(
        UUID id,
        AppointmentStatus status,
        OffsetDateTime scheduledStartAt,
        OffsetDateTime checkedInAt,
        OffsetDateTime actualStartAt
) {
}
