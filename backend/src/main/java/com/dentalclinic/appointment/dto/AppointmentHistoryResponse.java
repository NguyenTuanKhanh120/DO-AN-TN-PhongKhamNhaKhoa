package com.dentalclinic.appointment.dto;

import com.dentalclinic.appointment.entity.AppointmentAction;
import com.dentalclinic.user.entity.Role;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record AppointmentHistoryResponse(
        UUID id,
        UUID appointmentId,
        AppointmentAction action,
        UUID changedByUserId,
        String changedByFullName,
        Role changedByRole,
        String reason,
        JsonNode details,
        Instant createdAt
) {
}
