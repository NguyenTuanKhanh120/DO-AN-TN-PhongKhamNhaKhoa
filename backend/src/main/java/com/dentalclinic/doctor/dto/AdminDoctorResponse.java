package com.dentalclinic.doctor.dto;

import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.user.entity.UserStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminDoctorResponse(
        UUID id,
        UUID userId,
        String email,
        String fullName,
        UserStatus accountStatus,
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
