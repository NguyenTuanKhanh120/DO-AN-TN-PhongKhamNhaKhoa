package com.dentalclinic.ai.conversation.model;

import java.time.LocalDate;
import java.util.Objects;

public record ChatDateRange(
        LocalDate from,
        LocalDate to
) {

    public ChatDateRange {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("Chat date range end must not be before start");
        }
    }

    public static ChatDateRange single(LocalDate date) {
        return new ChatDateRange(date, date);
    }

    public boolean isSingleDay() {
        return from.equals(to);
    }
}
