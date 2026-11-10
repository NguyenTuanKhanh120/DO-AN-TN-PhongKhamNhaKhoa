package com.dentalclinic.appointment.dto;

import com.dentalclinic.schedule.entity.ScheduleSession;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RescheduleAppointmentRequest(
        @NotNull(message = "Ngày khám mới không được để trống")
        LocalDate appointmentDate,

        @NotNull(message = "Buổi khám mới không được để trống")
        ScheduleSession session,

        @NotNull(message = "Số ca mới không được để trống")
        @Min(value = 1, message = "Số ca chỉ được là 1 hoặc 2")
        @Max(value = 2, message = "Số ca chỉ được là 1 hoặc 2")
        Integer queueNumber,

        @NotBlank(message = "Lý do đổi lịch không được để trống")
        @Size(max = 100, message = "Lý do đổi lịch không được vượt quá 100 ký tự")
        String reason
) {
}
