package com.dentalclinic.ai.conversation.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public record ChatUnderstanding(
        List<ChatIntent> intents,
        ChatEntities entities,
        ChatFollowUpType followUpType
) {

    public ChatUnderstanding {
        Objects.requireNonNull(intents, "intents must not be null");
        Objects.requireNonNull(entities, "entities must not be null");
        Objects.requireNonNull(followUpType, "followUpType must not be null");
        intents = List.copyOf(new LinkedHashSet<>(intents));
        if (intents.isEmpty()) {
            throw new IllegalArgumentException("At least one chat intent is required");
        }
        if (intents.size() > 1 && intents.contains(ChatIntent.OUT_OF_SCOPE)) {
            throw new IllegalArgumentException("OUT_OF_SCOPE cannot be combined with another intent");
        }
    }
}
