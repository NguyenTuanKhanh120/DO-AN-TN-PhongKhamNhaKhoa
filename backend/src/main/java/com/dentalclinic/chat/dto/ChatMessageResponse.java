package com.dentalclinic.chat.dto;

import com.dentalclinic.chat.entity.ChatSender;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Một tin nhắn trong lịch sử chatbot")
public record ChatMessageResponse(
        UUID id,
        ChatSender sender,
        String content,
        JsonNode metadata,
        Instant createdAt
) {
}
