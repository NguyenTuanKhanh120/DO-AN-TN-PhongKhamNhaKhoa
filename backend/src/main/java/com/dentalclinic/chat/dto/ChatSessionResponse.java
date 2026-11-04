package com.dentalclinic.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Phiên chatbot duy nhất của bệnh nhân hiện tại")
public record ChatSessionResponse(
        UUID id,
        String title,
        Instant createdAt,
        Instant updatedAt
) {
}
