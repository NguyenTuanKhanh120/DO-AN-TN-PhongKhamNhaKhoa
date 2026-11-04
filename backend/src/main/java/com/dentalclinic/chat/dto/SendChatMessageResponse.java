package com.dentalclinic.chat.dto;

import com.dentalclinic.ai.conversation.model.ChatIntent;
import com.dentalclinic.ai.slot.model.ChatSlotSuggestion;
import com.dentalclinic.doctor.dto.DoctorResponse;
import com.dentalclinic.service.dto.DentalServiceResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Câu trả lời chatbot cùng dữ liệu RAG và gợi ý nghiệp vụ đã được Backend xác minh")
public record SendChatMessageResponse(
        UUID userMessageId,
        UUID messageId,
        String answer,
        List<ChatIntent> intents,
        List<ChatSourceResponse> sources,
        List<DentalServiceResponse> suggestedServices,
        List<DoctorResponse> suggestedDoctors,
        List<ChatSlotSuggestion> suggestedSlots,
        boolean needsClarification,
        Instant createdAt
) {

    public SendChatMessageResponse {
        intents = List.copyOf(intents);
        sources = List.copyOf(sources);
        suggestedServices = List.copyOf(suggestedServices);
        suggestedDoctors = List.copyOf(suggestedDoctors);
        suggestedSlots = List.copyOf(suggestedSlots);
    }
}
