package com.dentalclinic.ai.orchestration.model;

import com.dentalclinic.ai.conversation.model.ChatSemanticContext;
import com.dentalclinic.doctor.dto.DoctorResponse;
import com.dentalclinic.service.dto.DentalServiceResponse;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record DatabaseGroundingContext(
        ChatSemanticContext context,
        DentalServiceResponse selectedService,
        DoctorResponse selectedDoctor,
        UUID excludedDoctorId,
        UUID excludedServiceId
) {

    public DatabaseGroundingContext {
        Objects.requireNonNull(context, "context must not be null");
    }

    public Optional<DentalServiceResponse> selectedServiceOptional() {
        return Optional.ofNullable(selectedService);
    }

    public Optional<DoctorResponse> selectedDoctorOptional() {
        return Optional.ofNullable(selectedDoctor);
    }
}
