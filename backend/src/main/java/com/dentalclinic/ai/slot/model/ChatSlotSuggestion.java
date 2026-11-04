package com.dentalclinic.ai.slot.model;

import com.dentalclinic.ai.conversation.model.ChatSessionPreference;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record ChatSlotSuggestion(
        UUID doctorId,
        UUID serviceId,
        LocalDate date,
        ChatSessionPreference session,
        OffsetDateTime startAt,
        OffsetDateTime endAt
) {

    public ChatSlotSuggestion {
        Objects.requireNonNull(doctorId, "doctorId must not be null");
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(startAt, "startAt must not be null");
        Objects.requireNonNull(endAt, "endAt must not be null");
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("Slot endAt must be after startAt");
        }
    }
}
