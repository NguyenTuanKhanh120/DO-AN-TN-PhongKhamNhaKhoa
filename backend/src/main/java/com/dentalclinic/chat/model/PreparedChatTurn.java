package com.dentalclinic.chat.model;

import com.dentalclinic.ai.conversation.model.ChatContextResolution;
import com.dentalclinic.ai.orchestration.model.ChatGroundingResult;
import com.dentalclinic.ai.orchestration.model.ChatPrompt;

import java.util.Objects;
import java.util.UUID;

public record PreparedChatTurn(
        UUID userId,
        ChatTurnStart turn,
        ChatContextResolution resolution,
        ChatGroundingResult grounding,
        ChatPrompt prompt,
        String immediateAnswer
) {

    public PreparedChatTurn {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(turn, "turn must not be null");
        Objects.requireNonNull(resolution, "resolution must not be null");
        Objects.requireNonNull(grounding, "grounding must not be null");
        if ((prompt == null) == (immediateAnswer == null)) {
            throw new IllegalArgumentException(
                    "Exactly one of prompt or immediateAnswer must be provided"
            );
        }
        if (immediateAnswer != null && immediateAnswer.isBlank()) {
            throw new IllegalArgumentException("immediateAnswer must not be blank");
        }
    }

    public boolean requiresGeneration() {
        return prompt != null;
    }
}
