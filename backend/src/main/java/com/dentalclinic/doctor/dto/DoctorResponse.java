package com.dentalclinic.doctor.dto;

import com.dentalclinic.doctor.entity.DoctorStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DoctorResponse(
        UUID id,
        String fullName,
        String specialization,
        String licenseNumber,
        String bio,
        BigDecimal rating,
        int experienceYears,
        DoctorStatus workingStatus,
        Instant createdAt,
        Instant updatedAt
) {
}
