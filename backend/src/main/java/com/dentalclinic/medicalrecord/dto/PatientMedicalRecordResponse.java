package com.dentalclinic.medicalrecord.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PatientMedicalRecordResponse(
        UUID id,
        UUID appointmentId,
        UUID doctorId,
        String doctorFullName,
        String doctorSpecialization,
        UUID serviceId,
        String serviceName,
        OffsetDateTime scheduledStartAt,
        OffsetDateTime actualStartAt,
        OffsetDateTime actualEndAt,
        String diagnosis,
        String treatment,
        String treatmentPlan,
        String notes,
        List<PrescriptionItem> prescription,
        boolean followUpRequired,
        LocalDate recommendedFollowUpDate,
        String followUpNote,
        Instant createdAt,
        Instant updatedAt
) {
    public PatientMedicalRecordResponse {
        prescription = List.copyOf(prescription);
    }
}
