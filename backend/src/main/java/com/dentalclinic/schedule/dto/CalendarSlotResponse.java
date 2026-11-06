package com.dentalclinic.schedule.dto;

import java.time.LocalTime;
import java.time.OffsetDateTime;

public record CalendarSlotResponse(
        int queueNumber,
        LocalTime estimatedStartTime,
        OffsetDateTime startAt,
        OffsetDateTime endAt
) {

    public CalendarSlotResponse(OffsetDateTime startAt, OffsetDateTime endAt) {
        this(0, startAt.toLocalTime(), startAt, endAt);
    }
}
