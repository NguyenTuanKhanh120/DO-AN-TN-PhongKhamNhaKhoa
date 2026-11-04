package com.dentalclinic.chat.dto;

import com.dentalclinic.ai.conversation.model.ChatIntent;
import com.dentalclinic.ai.slot.model.ChatSlotSuggestion;
import com.dentalclinic.doctor.dto.DoctorResponse;
import com.dentalclinic.service.dto.DentalServiceResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Dữ liệu gợi ý đã được Backend xác minh sau khi hoàn tất nội dung")
public record ChatStreamMetadataEvent(
        List<ChatIntent> intents,
        List<ChatSourceResponse> sources,
        List<DentalServiceResponse> suggestedServices,
        List<DoctorResponse> suggestedDoctors,
        List<ChatSlotSuggestion> suggestedSlots,
        boolean needsClarification
) {

    public ChatStreamMetadataEvent {
        intents = List.copyOf(intents);
        sources = List.copyOf(sources);
        suggestedServices = List.copyOf(suggestedServices);
        suggestedDoctors = List.copyOf(suggestedDoctors);
        suggestedSlots = List.copyOf(suggestedSlots);
    }
}
