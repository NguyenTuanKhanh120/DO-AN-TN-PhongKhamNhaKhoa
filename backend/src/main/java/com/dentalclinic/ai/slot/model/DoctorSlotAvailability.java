package com.dentalclinic.ai.slot.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record DoctorSlotAvailability(
        UUID doctorId,
        UUID serviceId,
        List<ChatSlotSuggestion> slots
) {

    public DoctorSlotAvailability {
        Objects.requireNonNull(doctorId, "doctorId must not be null");
        slots = List.copyOf(Objects.requireNonNull(slots, "slots must not be null"));
    }
}
