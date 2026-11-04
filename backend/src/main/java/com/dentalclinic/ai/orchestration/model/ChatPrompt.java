package com.dentalclinic.ai.orchestration.model;

import java.util.Objects;

public record ChatPrompt(
        String systemPrompt,
        String userPrompt
) {

    public ChatPrompt {
        systemPrompt = requireText(systemPrompt, "systemPrompt");
        userPrompt = requireText(userPrompt, "userPrompt");
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }
}
