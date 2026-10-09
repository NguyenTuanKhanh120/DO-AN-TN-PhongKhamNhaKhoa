package com.dentalclinic.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelAppointmentRequest(
        @NotBlank(message = "Lý do hủy không được để trống")
        @Size(max = 100, message = "Lý do hủy không được vượt quá 100 ký tự")
        String reason
) {
}
