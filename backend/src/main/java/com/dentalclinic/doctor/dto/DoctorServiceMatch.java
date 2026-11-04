package com.dentalclinic.doctor.dto;

import java.util.Objects;
import java.util.UUID;

public record DoctorServiceMatch(
        UUID serviceId,
        DoctorResponse doctor
) {

    public DoctorServiceMatch {
        Objects.requireNonNull(serviceId, "serviceId must not be null");
        Objects.requireNonNull(doctor, "doctor must not be null");
    }
}
