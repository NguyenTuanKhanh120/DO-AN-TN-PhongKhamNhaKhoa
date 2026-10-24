package com.dentalclinic.doctor.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record DoctorAssignedServiceResponse(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        long durationMinutes,
        String imageUrl,
        boolean active
) {
}
