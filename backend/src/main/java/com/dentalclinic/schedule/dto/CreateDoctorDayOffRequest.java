package com.dentalclinic.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateDoctorDayOffRequest(
        @NotNull(message = "Ngày nghỉ không được để trống")
        LocalDate offDate,

        @NotNull(message = "Buổi nghỉ không được để trống")
        DoctorDayOffPeriod session,

        @NotBlank(message = "Lý do nghỉ không được để trống")
        @Size(max = 255, message = "Lý do nghỉ không được vượt quá 255 ký tự")
        String reason
) {
}
