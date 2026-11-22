package com.dentalclinic.ai.orchestration.model;

import com.dentalclinic.ai.slot.model.ChatSlotSuggestion;
import com.dentalclinic.doctor.dto.DoctorResponse;
import com.dentalclinic.service.dto.DentalServiceResponse;

import java.util.List;
import java.util.Objects;

public record DatabaseQueryResult(
        List<DentalServiceResponse> services,
        List<DoctorResponse> doctors,
        List<ChatSlotSuggestion> slots
) {

    public DatabaseQueryResult {
        services = copy(services, "services");
        doctors = copy(doctors, "doctors");
        slots = copy(slots, "slots");
    }

    private static <T> List<T> copy(List<T> values, String fieldName) {
        return List.copyOf(Objects.requireNonNull(values, fieldName + " must not be null"));
    }
}
