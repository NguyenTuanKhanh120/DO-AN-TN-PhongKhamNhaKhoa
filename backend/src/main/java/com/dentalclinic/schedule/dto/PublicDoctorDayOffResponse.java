package com.dentalclinic.schedule.dto;

import com.dentalclinic.schedule.entity.ScheduleSession;

import java.time.LocalDate;
import java.util.UUID;

public record PublicDoctorDayOffResponse(
        UUID id,
        UUID doctorId,
        LocalDate offDate,
        ScheduleSession session
) {
}
