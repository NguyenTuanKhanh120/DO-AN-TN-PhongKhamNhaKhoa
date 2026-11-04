package com.dentalclinic.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Dữ liệu SSE xác nhận câu trả lời hoàn chỉnh đã được lưu")
public record ChatStreamCompletedEvent(
        UUID messageId,
        Instant createdAt
) {
}
