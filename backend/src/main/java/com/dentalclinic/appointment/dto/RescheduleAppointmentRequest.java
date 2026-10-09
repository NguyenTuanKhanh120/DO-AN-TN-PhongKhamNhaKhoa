package com.dentalclinic.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record RescheduleAppointmentRequest(
        @NotNull(message = "Khung giờ mới không được để trống")
        OffsetDateTime scheduledStartAt,

        @NotBlank(message = "Lý do đổi lịch không được để trống")
        @Size(max = 100, message = "Lý do đổi lịch không được vượt quá 100 ký tự")
        String reason
) {
}
