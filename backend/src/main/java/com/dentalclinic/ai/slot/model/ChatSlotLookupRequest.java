package com.dentalclinic.ai.slot.model;

import com.dentalclinic.ai.conversation.model.ChatSessionPreference;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public record ChatSlotLookupRequest(
        List<SlotLookupTarget> targets,
        LocalDate fromDate,
        LocalDate toDate,
        ChatSessionPreference sessionPreference
) {

    public ChatSlotLookupRequest {
        Objects.requireNonNull(targets, "targets must not be null");
        Objects.requireNonNull(fromDate, "fromDate must not be null");
        targets.forEach(target -> Objects.requireNonNull(target, "target must not be null"));
        targets = List.copyOf(new LinkedHashSet<>(targets));
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("At least one slot lookup target is required");
        }
        toDate = toDate == null ? fromDate : toDate;
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("toDate must not be before fromDate");
        }
    }
}
