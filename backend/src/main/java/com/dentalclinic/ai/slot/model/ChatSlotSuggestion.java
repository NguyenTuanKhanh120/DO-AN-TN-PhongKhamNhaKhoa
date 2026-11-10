package com.dentalclinic.ai.slot.model;

import com.dentalclinic.ai.conversation.model.ChatSessionPreference;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Schema(description = "Ca khám còn trống do Backend scheduling xác minh; giờ bắt đầu chỉ là dự kiến")
public record ChatSlotSuggestion(
        UUID doctorId,
        UUID serviceId,
        LocalDate date,
        ChatSessionPreference session,
        @Schema(description = "Số thứ tự ca trong buổi", allowableValues = {"1", "2"})
        int queueNumber,
        LocalTime sessionStartTime,
        LocalTime sessionEndTime,
        LocalTime estimatedStartTime,
        OffsetDateTime estimatedStartAt
) {

    public ChatSlotSuggestion {
        Objects.requireNonNull(doctorId, "doctorId must not be null");
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(sessionStartTime, "sessionStartTime must not be null");
        Objects.requireNonNull(sessionEndTime, "sessionEndTime must not be null");
        Objects.requireNonNull(estimatedStartTime, "estimatedStartTime must not be null");
        Objects.requireNonNull(estimatedStartAt, "estimatedStartAt must not be null");
        if (queueNumber < 1 || queueNumber > 2) {
            throw new IllegalArgumentException("queueNumber must be 1 or 2");
        }
        if (!sessionEndTime.isAfter(sessionStartTime)) {
            throw new IllegalArgumentException(
                    "sessionEndTime must be after sessionStartTime"
            );
        }
        if (estimatedStartTime.isBefore(sessionStartTime)
                || !estimatedStartTime.isBefore(sessionEndTime)) {
            throw new IllegalArgumentException(
                    "estimatedStartTime must be within the session"
            );
        }
        if (!estimatedStartAt.toLocalDate().equals(date)
                || !estimatedStartAt.toLocalTime().equals(estimatedStartTime)) {
            throw new IllegalArgumentException(
                    "estimatedStartAt must match date and estimatedStartTime"
            );
        }
    }
}
