package com.dentalclinic.ai.orchestration.model;

import com.dentalclinic.ai.conversation.model.ChatIntent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public record ChatQueryPlan(
        ChatQueryRoute route,
        List<ChatIntent> intents
) {

    public ChatQueryPlan {
        Objects.requireNonNull(route, "route must not be null");
        Objects.requireNonNull(intents, "intents must not be null");
        intents = List.copyOf(new LinkedHashSet<>(intents));
    }

    public boolean requiresDatabase() {
        return route == ChatQueryRoute.DB_ONLY || route == ChatQueryRoute.HYBRID;
    }

    public boolean requiresRag() {
        return route == ChatQueryRoute.RAG_ONLY || route == ChatQueryRoute.HYBRID;
    }
}
