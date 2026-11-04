package com.dentalclinic.ai.recommendation.model;

import com.dentalclinic.doctor.dto.DoctorResponse;
import com.dentalclinic.service.dto.DentalServiceResponse;

import java.util.List;
import java.util.Objects;

public record ServiceDoctorRecommendation(
        DentalServiceResponse service,
        List<DoctorResponse> doctors
) {

    public ServiceDoctorRecommendation {
        Objects.requireNonNull(service, "service must not be null");
        doctors = List.copyOf(Objects.requireNonNull(doctors, "doctors must not be null"));
        if (!service.active()) {
            throw new IllegalArgumentException("Recommended dental service must be active");
        }
        if (doctors.isEmpty()) {
            throw new IllegalArgumentException(
                    "Recommended dental service must have at least one active doctor"
            );
        }
    }
}
