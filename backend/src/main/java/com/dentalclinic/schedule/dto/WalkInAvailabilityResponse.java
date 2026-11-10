package com.dentalclinic.schedule.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record WalkInAvailabilityResponse(
        UUID doctorId,
        UUID serviceId,
        LocalDate date,
        List<WalkInSessionAvailabilityResponse> sessions
) {
}
