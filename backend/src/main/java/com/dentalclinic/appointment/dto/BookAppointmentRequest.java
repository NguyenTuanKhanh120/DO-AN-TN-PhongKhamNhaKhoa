package com.dentalclinic.appointment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.dentalclinic.schedule.entity.ScheduleSession;

import java.time.LocalDate;
import java.util.UUID;

public record BookAppointmentRequest(
        @NotNull(message = "Bác sĩ không được để trống")
        UUID doctorId,

        @NotNull(message = "Dịch vụ không được để trống")
        UUID serviceId,

        @NotNull(message = "Ngày khám không được để trống")
        @Schema(description = "Ngày khám theo Asia/Ho_Chi_Minh, từ hôm nay đến hôm nay + 29 ngày; lịch hôm nay phải đặt trước 05:00")
        LocalDate appointmentDate,

        @NotNull(message = "Buổi khám không được để trống")
        ScheduleSession session,

        @NotNull(message = "Số ca không được để trống")
        @Min(value = 1, message = "Số ca chỉ được là 1 hoặc 2")
        @Max(value = 2, message = "Số ca chỉ được là 1 hoặc 2")
        Integer queueNumber,

        UUID previousAppointmentId
) {
}
