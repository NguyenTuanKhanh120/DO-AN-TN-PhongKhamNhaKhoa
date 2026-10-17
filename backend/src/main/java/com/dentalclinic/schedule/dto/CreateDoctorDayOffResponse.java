package com.dentalclinic.schedule.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateDoctorDayOffResponse(
        UUID doctorId,
        String doctorFullName,
        LocalDate offDate,
        DoctorDayOffPeriod requestedSession,
        List<AdminDoctorDayOffResponse> createdDaysOff,
        int cancelledAppointmentCount,
        List<DayOffAffectedAppointmentResponse> cancelledAppointments,
        int manualReviewAppointmentCount,
        List<DayOffAffectedAppointmentResponse> manualReviewAppointments
) {
    public CreateDoctorDayOffResponse {
        createdDaysOff = List.copyOf(createdDaysOff);
        cancelledAppointments = List.copyOf(cancelledAppointments);
        manualReviewAppointments = List.copyOf(manualReviewAppointments);
    }
}
