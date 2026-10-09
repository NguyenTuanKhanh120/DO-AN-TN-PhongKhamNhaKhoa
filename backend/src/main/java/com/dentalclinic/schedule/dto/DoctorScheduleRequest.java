package com.dentalclinic.schedule.dto;

import com.dentalclinic.schedule.entity.ScheduleSession;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record DoctorScheduleRequest(
        @Min(value = 1, message = "Ngày trong tuần phải từ 1 đến 7")
        @Max(value = 7, message = "Ngày trong tuần phải từ 1 đến 7")
        int dayOfWeek,

        @NotNull(message = "Buổi làm việc không được để trống")
        ScheduleSession session,

        @NotNull(message = "Giờ bắt đầu không được để trống")
        LocalTime startTime,

        @NotNull(message = "Giờ kết thúc không được để trống")
        LocalTime endTime
) {
}
