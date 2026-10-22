package com.dentalclinic.schedule.dto;

import java.time.OffsetDateTime;

public record CalendarSlotResponse(
        OffsetDateTime startAt,
        OffsetDateTime endAt
) {
}
