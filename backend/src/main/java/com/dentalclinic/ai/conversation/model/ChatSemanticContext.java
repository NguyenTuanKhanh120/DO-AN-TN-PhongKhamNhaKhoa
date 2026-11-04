package com.dentalclinic.ai.conversation.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public record ChatSemanticContext(
        List<ChatIntent> lastIntents,
        List<String> symptoms,
        ChatEntityReference selectedDoctor,
        ChatEntityReference selectedService,
        ChatDateRange dateRange,
        ChatSessionPreference sessionPreference,
        String timePreference
) {

    public ChatSemanticContext {
        lastIntents = lastIntents == null
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(lastIntents));
        symptoms = symptoms == null ? List.of() : List.copyOf(new ArrayList<>(symptoms));
        timePreference = timePreference == null || timePreference.isBlank()
                ? null
                : timePreference.strip();
    }

    public static ChatSemanticContext empty() {
        return new ChatSemanticContext(
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null
        );
    }
}
