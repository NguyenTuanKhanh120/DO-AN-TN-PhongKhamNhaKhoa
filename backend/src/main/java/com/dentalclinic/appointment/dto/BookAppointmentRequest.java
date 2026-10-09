package com.dentalclinic.appointment.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BookAppointmentRequest(
        @NotNull(message = "Bác sĩ không được để trống")
        UUID doctorId,

        @NotNull(message = "Dịch vụ không được để trống")
        UUID serviceId,

        @NotNull(message = "Thời gian bắt đầu không được để trống")
        OffsetDateTime scheduledStartAt
) {
}
