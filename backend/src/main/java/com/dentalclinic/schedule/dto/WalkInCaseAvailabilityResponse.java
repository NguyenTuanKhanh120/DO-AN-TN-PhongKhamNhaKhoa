package com.dentalclinic.schedule.dto;

import java.time.LocalTime;
import java.time.OffsetDateTime;

public record WalkInCaseAvailabilityResponse(
        int queueNumber,
        LocalTime estimatedStartTime,
        OffsetDateTime estimatedStartAt,
        boolean available
) {
}
