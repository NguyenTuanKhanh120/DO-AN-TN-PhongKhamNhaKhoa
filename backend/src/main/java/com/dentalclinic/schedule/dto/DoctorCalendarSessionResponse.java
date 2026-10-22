package com.dentalclinic.schedule.dto;

import com.dentalclinic.schedule.entity.ScheduleSession;

import java.util.List;

public record DoctorCalendarSessionResponse(
        ScheduleSession session,
        CalendarAvailabilityStatus status,
        int availableSlotCount,
        List<CalendarSlotResponse> availableSlots
) {
}
