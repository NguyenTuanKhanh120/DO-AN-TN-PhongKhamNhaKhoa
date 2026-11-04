package com.dentalclinic.ai.conversation.model;

import java.util.List;
import java.util.Objects;

public record ChatContextResolution(
        ChatSemanticContext context,
        boolean needsClarification,
        List<ClarificationField> missingFields,
        String clarificationQuestion,
        ChatFollowUpType followUpType
) {

    public ChatContextResolution {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(missingFields, "missingFields must not be null");
        Objects.requireNonNull(followUpType, "followUpType must not be null");
        missingFields = List.copyOf(missingFields);
        clarificationQuestion = clarificationQuestion == null || clarificationQuestion.isBlank()
                ? null
                : clarificationQuestion.strip();
        if (needsClarification != !missingFields.isEmpty()) {
            throw new IllegalArgumentException("Clarification state is inconsistent");
        }
        if (needsClarification && clarificationQuestion == null) {
            throw new IllegalArgumentException("Clarification question is required");
        }
    }
}
