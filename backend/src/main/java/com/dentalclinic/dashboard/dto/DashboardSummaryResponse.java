package com.dentalclinic.dashboard.dto;

public record DashboardSummaryResponse(
        long patientsVisitedToday,
        long totalDoctors,
        long todayAppointments,
        long completedToday,
        long cancelledToday,
        long noShowToday
) {
}
