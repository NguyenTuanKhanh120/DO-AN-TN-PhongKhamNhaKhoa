package com.dentalclinic.ai.conversation.model;

import java.util.UUID;

public record ChatEntityReference(
        UUID id,
        String name
) {

    public ChatEntityReference {
        name = normalize(name);
        if (id == null && name == null) {
            throw new IllegalArgumentException("Chat entity reference requires an id or name");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
