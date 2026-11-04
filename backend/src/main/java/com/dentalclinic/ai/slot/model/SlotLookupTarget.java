package com.dentalclinic.ai.slot.model;

import java.util.Objects;
import java.util.UUID;

public record SlotLookupTarget(
        UUID doctorId,
        UUID serviceId
) {

    public SlotLookupTarget {
        Objects.requireNonNull(doctorId, "doctorId must not be null");
    }
}
