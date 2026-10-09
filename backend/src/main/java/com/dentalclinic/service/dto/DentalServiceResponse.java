package com.dentalclinic.service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DentalServiceResponse(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
