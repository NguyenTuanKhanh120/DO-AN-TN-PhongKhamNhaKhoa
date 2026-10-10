package com.dentalclinic.dashboard.dto;

public record DashboardSummaryResponse(
        long totalPatients,
        long totalDoctors,
        long todayAppointments,
        long completedToday,
        long cancelledToday,
        long noShowToday
) {
}
