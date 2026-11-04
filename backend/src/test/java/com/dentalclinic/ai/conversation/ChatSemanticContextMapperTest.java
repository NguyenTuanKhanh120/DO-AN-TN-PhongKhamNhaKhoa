package com.dentalclinic.ai.conversation;

import com.dentalclinic.ai.conversation.mapper.ChatSemanticContextMapper;
import com.dentalclinic.ai.conversation.model.ChatDateRange;
import com.dentalclinic.ai.conversation.model.ChatEntityReference;
import com.dentalclinic.ai.conversation.model.ChatIntent;
import com.dentalclinic.ai.conversation.model.ChatSemanticContext;
import com.dentalclinic.ai.conversation.model.ChatSessionPreference;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatSemanticContextMapperTest {

    private final ChatSemanticContextMapper mapper = new ChatSemanticContextMapper();

    @Test
    void shouldRoundTripSemanticContextWithoutCurrentStep() {
        UUID doctorId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        ChatSemanticContext context = new ChatSemanticContext(
                List.of(ChatIntent.DOCTOR_RECOMMENDATION, ChatIntent.AVAILABLE_SLOTS),
                List.of("đau răng", "sưng lợi"),
                new ChatEntityReference(doctorId, "Bác sĩ Nguyễn Văn A"),
                new ChatEntityReference(serviceId, "Khám răng tổng quát"),
                new ChatDateRange(
                        LocalDate.of(2026, 10, 30),
                        LocalDate.of(2026, 10, 31)
                ),
                ChatSessionPreference.AFTERNOON,
                "sau 14:00"
        );

        var json = mapper.toJson(context);
        ChatSemanticContext restored = mapper.fromJson(json);

        assertThat(json.has("currentStep")).isFalse();
        assertThat(restored).isEqualTo(context);
    }

    @Test
    void shouldReturnEmptyContextForEmptyJsonObject() {
        assertThat(mapper.fromJson(JsonNodeFactory.instance.objectNode()))
                .isEqualTo(ChatSemanticContext.empty());
    }

    @Test
    void shouldRejectMalformedStoredContext() {
        var json = JsonNodeFactory.instance.objectNode();
        json.putArray("lastIntents").add("UNKNOWN_INTENT");

        assertThatThrownBy(() -> mapper.fromJson(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Chat semantic context is invalid");
    }
}
