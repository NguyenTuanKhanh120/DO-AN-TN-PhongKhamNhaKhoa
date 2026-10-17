package com.dentalclinic.schedule.dto;

import com.dentalclinic.schedule.entity.ScheduleSession;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminDoctorDayOffResponse(
        UUID id,
        UUID doctorId,
        String doctorFullName,
        LocalDate offDate,
        ScheduleSession session,
        String reason,
        UUID createdByUserId,
        String createdByFullName,
        Instant createdAt
) {
}
