package com.dentalclinic.ai.slot.model;

import com.dentalclinic.ai.conversation.model.ChatSessionPreference;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record ChatSlotLookupResult(
        LocalDate fromDate,
        LocalDate toDate,
        ChatSessionPreference sessionPreference,
        List<DoctorSlotAvailability> availability
) {

    public ChatSlotLookupResult {
        Objects.requireNonNull(fromDate, "fromDate must not be null");
        Objects.requireNonNull(toDate, "toDate must not be null");
        availability = List.copyOf(Objects.requireNonNull(
                availability,
                "availability must not be null"
        ));
    }
}
