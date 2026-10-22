package com.dentalclinic.schedule.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DoctorCalendarResponse(
        UUID doctorId,
        UUID serviceId,
        LocalDate fromDate,
        LocalDate toDate,
        List<DoctorCalendarDayResponse> days
) {
}
