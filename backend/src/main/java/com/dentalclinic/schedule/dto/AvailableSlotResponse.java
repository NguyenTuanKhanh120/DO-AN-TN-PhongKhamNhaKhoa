package com.dentalclinic.schedule.dto;

import com.dentalclinic.schedule.entity.ScheduleSession;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

public record AvailableSlotResponse(
        LocalDate date,
        ScheduleSession session,
        LocalTime startTime,
        LocalTime endTime,
        OffsetDateTime startAt,
        OffsetDateTime endAt
) {
}
