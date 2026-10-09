package com.dentalclinic.schedule.dto;

import com.dentalclinic.schedule.entity.ScheduleSession;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record DoctorScheduleResponse(
        UUID id,
        UUID doctorId,
        int dayOfWeek,
        ScheduleSession session,
        LocalTime startTime,
        LocalTime endTime,
        Instant createdAt,
        Instant updatedAt
) {
}
