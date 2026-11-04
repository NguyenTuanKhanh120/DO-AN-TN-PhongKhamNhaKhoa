package com.dentalclinic.ai.orchestration.model;

import com.dentalclinic.ai.conversation.model.ChatSemanticContext;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalResult;
import com.dentalclinic.ai.slot.model.ChatSlotSuggestion;
import com.dentalclinic.doctor.dto.DoctorResponse;
import com.dentalclinic.service.dto.DentalServiceResponse;

import java.util.List;
import java.util.Objects;

public record ChatGroundingResult(
        ChatSemanticContext context,
        boolean knowledgeRequired,
        boolean knowledgeAvailable,
        List<KnowledgeRetrievalResult> knowledge,
        List<DentalServiceResponse> services,
        List<DoctorResponse> doctors,
        List<ChatSlotSuggestion> slots
) {

    public ChatGroundingResult {
        Objects.requireNonNull(context, "context must not be null");
        knowledge = copy(knowledge, "knowledge");
        services = copy(services, "services");
        doctors = copy(doctors, "doctors");
        slots = copy(slots, "slots");
    }

    public static ChatGroundingResult empty(ChatSemanticContext context) {
        return new ChatGroundingResult(
                context,
                false,
                true,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static <T> List<T> copy(List<T> values, String fieldName) {
        return List.copyOf(Objects.requireNonNull(values, fieldName + " must not be null"));
    }
}
