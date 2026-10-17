package com.dentalclinic.schedule.dto;

import com.dentalclinic.appointment.entity.AppointmentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DayOffAffectedAppointmentResponse(
        UUID appointmentId,
        UUID patientId,
        String patientFullName,
        String patientPhone,
        UUID serviceId,
        String serviceName,
        OffsetDateTime scheduledStartAt,
        AppointmentStatus status
) {
}
