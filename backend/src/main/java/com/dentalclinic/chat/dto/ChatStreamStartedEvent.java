package com.dentalclinic.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Dữ liệu SSE khi Backend đã lưu tin nhắn của bệnh nhân")
public record ChatStreamStartedEvent(
        UUID userMessageId,
        Instant startedAt
) {
}
