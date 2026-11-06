package com.dentalclinic.appointment.dto;

import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.schedule.entity.ScheduleSession;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record StartExaminationResponse(
        UUID id,
        AppointmentStatus status,
        LocalDate appointmentDate,
        ScheduleSession session,
        int queueNumber,
        OffsetDateTime estimatedStartAt,
        OffsetDateTime checkedInAt,
        OffsetDateTime actualStartAt
) {
}
