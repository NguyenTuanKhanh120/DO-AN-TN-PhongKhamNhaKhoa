package com.dentalclinic.chat.model;

import com.dentalclinic.ai.conversation.model.ChatContextResolution;
import com.dentalclinic.ai.orchestration.model.ChatGroundingResult;
import com.dentalclinic.ai.orchestration.model.ChatPrompt;
import com.dentalclinic.ai.telemetry.ChatTelemetry;

import java.util.Objects;
import java.util.UUID;

public record PreparedChatTurn(
        UUID userId,
        ChatTurnStart turn,
        ChatContextResolution resolution,
        ChatGroundingResult grounding,
        ChatPrompt prompt,
        String immediateAnswer,
        String generationFailureFallback,
        String requestId,
        long requestStartedNanos
) {

    public PreparedChatTurn(
            UUID userId,
            ChatTurnStart turn,
            ChatContextResolution resolution,
            ChatGroundingResult grounding,
            ChatPrompt prompt,
            String immediateAnswer,
            String generationFailureFallback
    ) {
        this(
                userId,
                turn,
                resolution,
                grounding,
                prompt,
                immediateAnswer,
                generationFailureFallback,
                ChatTelemetry.newRequestId(),
                System.nanoTime()
        );
    }

    public PreparedChatTurn {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(turn, "turn must not be null");
        Objects.requireNonNull(resolution, "resolution must not be null");
        Objects.requireNonNull(grounding, "grounding must not be null");
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        if (requestStartedNanos <= 0L) {
            throw new IllegalArgumentException("requestStartedNanos must be positive");
        }
        if ((prompt == null) == (immediateAnswer == null)) {
            throw new IllegalArgumentException(
                    "Exactly one of prompt or immediateAnswer must be provided"
            );
        }
        if (immediateAnswer != null && immediateAnswer.isBlank()) {
            throw new IllegalArgumentException("immediateAnswer must not be blank");
        }
        if (generationFailureFallback != null && generationFailureFallback.isBlank()) {
            throw new IllegalArgumentException(
                    "generationFailureFallback must not be blank"
            );
        }
        if (prompt == null && generationFailureFallback != null) {
            throw new IllegalArgumentException(
                    "generationFailureFallback requires a generation prompt"
            );
        }
    }

    public boolean requiresGeneration() {
        return prompt != null;
    }
}
