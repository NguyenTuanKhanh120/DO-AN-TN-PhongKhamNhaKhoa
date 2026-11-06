package com.dentalclinic.schedule.dto;

import com.dentalclinic.schedule.entity.ScheduleSession;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

public record AvailableSlotResponse(
        LocalDate date,
        ScheduleSession session,
        int queueNumber,
        LocalTime sessionStartTime,
        LocalTime sessionEndTime,
        LocalTime estimatedStartTime,
        OffsetDateTime estimatedStartAt
) {
}
