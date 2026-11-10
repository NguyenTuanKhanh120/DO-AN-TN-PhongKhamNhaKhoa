package com.dentalclinic.schedule.dto;

import com.dentalclinic.schedule.entity.ScheduleSession;

import java.time.LocalTime;
import java.util.List;

public record WalkInSessionAvailabilityResponse(
        ScheduleSession session,
        CalendarAvailabilityStatus status,
        LocalTime sessionStartTime,
        LocalTime sessionEndTime,
        int availableCaseCount,
        List<WalkInCaseAvailabilityResponse> cases
) {
}
