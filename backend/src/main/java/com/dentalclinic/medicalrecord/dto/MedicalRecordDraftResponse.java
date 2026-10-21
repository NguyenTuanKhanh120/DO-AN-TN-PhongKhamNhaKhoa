package com.dentalclinic.medicalrecord.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MedicalRecordDraftResponse(
        UUID id,
        UUID appointmentId,
        String diagnosis,
        String treatment,
        String treatmentPlan,
        String notes,
        List<MedicalRecordDraftPrescriptionItem> prescription,
        Boolean followUpRequired,
        LocalDate recommendedFollowUpDate,
        String followUpNote,
        Instant createdAt,
        Instant updatedAt
) {
}
