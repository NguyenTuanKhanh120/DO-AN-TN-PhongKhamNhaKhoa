package com.dentalclinic.patient.dto;

import com.dentalclinic.patient.entity.Gender;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StaffPatientResponse(
        UUID id,
        String fullName,
        String phone,
        LocalDate dateOfBirth,
        Gender gender,
        String address,
        String medicalHistory,
        String allergies,
        Instant createdAt,
        Instant updatedAt
) {
}
