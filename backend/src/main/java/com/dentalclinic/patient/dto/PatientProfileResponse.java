package com.dentalclinic.patient.dto;

import com.dentalclinic.patient.entity.Gender;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PatientProfileResponse(
        UUID patientId,
        UUID userId,
        String email,
        String fullName,
        LocalDate dateOfBirth,
        Gender gender,
        String phone,
        String address,
        String medicalHistory,
        String allergies,
        Instant createdAt,
        Instant updatedAt
) {
}
