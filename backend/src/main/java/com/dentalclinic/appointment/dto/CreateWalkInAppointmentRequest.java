package com.dentalclinic.appointment.dto;

import com.dentalclinic.schedule.entity.ScheduleSession;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateWalkInAppointmentRequest(
        @NotNull(message = "Bệnh nhân không được để trống")
        UUID patientId,

        @NotNull(message = "Bác sĩ không được để trống")
        UUID doctorId,

        @NotNull(message = "Dịch vụ không được để trống")
        UUID serviceId,

        @NotNull(message = "Buổi khám không được để trống")
        ScheduleSession session,

        @NotNull(message = "Số ca không được để trống")
        @Min(value = 1, message = "Số ca chỉ được là 1 hoặc 2")
        @Max(value = 2, message = "Số ca chỉ được là 1 hoặc 2")
        Integer queueNumber
) {
}
