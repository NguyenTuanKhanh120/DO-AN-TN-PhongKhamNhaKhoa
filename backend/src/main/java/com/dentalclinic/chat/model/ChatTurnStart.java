package com.dentalclinic.chat.model;

import com.dentalclinic.chat.dto.ChatMessageResponse;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

public record ChatTurnStart(
        ChatMessageResponse userMessage,
        JsonNode contextData
) {

    public ChatTurnStart {
        Objects.requireNonNull(userMessage, "userMessage must not be null");
        Objects.requireNonNull(contextData, "contextData must not be null");
        contextData = contextData.deepCopy();
    }
}
