package com.dentalclinic.schedule.dto;

import java.time.LocalDate;
import java.util.List;

public record DoctorCalendarDayResponse(
        LocalDate date,
        int dayOfWeek,
        CalendarAvailabilityStatus status,
        List<DoctorCalendarSessionResponse> sessions
) {
}
