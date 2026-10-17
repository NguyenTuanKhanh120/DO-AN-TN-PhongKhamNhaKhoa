package com.dentalclinic.schedule.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DoctorDayOffPreviewResponse(
        UUID doctorId,
        String doctorFullName,
        LocalDate offDate,
        DoctorDayOffPeriod session,
        int appointmentsToCancelCount,
        List<DayOffAffectedAppointmentResponse> appointmentsToCancel,
        int blockingAppointmentsCount,
        List<DayOffAffectedAppointmentResponse> blockingAppointments,
        int manualReviewAppointmentsCount,
        List<DayOffAffectedAppointmentResponse> manualReviewAppointments,
        boolean canConfirm
) {
    public DoctorDayOffPreviewResponse {
        appointmentsToCancel = List.copyOf(appointmentsToCancel);
        blockingAppointments = List.copyOf(blockingAppointments);
        manualReviewAppointments = List.copyOf(manualReviewAppointments);
    }
}
