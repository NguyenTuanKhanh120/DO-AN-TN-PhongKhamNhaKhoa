package com.dentalclinic.ai.conversation.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public record ChatEntities(
        List<String> symptoms,
        String doctorName,
        UUID doctorId,
        String serviceName,
        UUID serviceId,
        LocalDate date,
        LocalDate dateFrom,
        LocalDate dateTo,
        ChatSessionPreference sessionPreference,
        String timePreference
) {

    public ChatEntities {
        symptoms = normalizeList(symptoms);
        doctorName = normalize(doctorName);
        serviceName = normalize(serviceName);
        timePreference = normalize(timePreference);
        if (date != null && (dateFrom != null || dateTo != null)) {
            throw new IllegalArgumentException("Use either an exact date or a date range");
        }
        if (dateFrom != null && dateTo != null && dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("Entity dateTo must not be before dateFrom");
        }
    }

    public static ChatEntities empty() {
        return new ChatEntities(
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public Optional<ChatDateRange> completeDateRange() {
        if (date != null) {
            return Optional.of(ChatDateRange.single(date));
        }
        if (dateFrom != null && dateTo != null) {
            return Optional.of(new ChatDateRange(dateFrom, dateTo));
        }
        return Optional.empty();
    }

    public boolean hasPartialDateRange() {
        return date == null && (dateFrom == null) != (dateTo == null);
    }

    public boolean hasTemporalRefinement() {
        return date != null
                || dateFrom != null
                || dateTo != null
                || sessionPreference != null
                || timePreference != null;
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                unique.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
            }
        }
        return List.copyOf(new ArrayList<>(unique.values()));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
