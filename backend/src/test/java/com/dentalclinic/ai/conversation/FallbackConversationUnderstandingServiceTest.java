package com.dentalclinic.ai.conversation;

import com.dentalclinic.ai.conversation.model.ChatEntityReference;
import com.dentalclinic.ai.conversation.model.ChatFollowUpType;
import com.dentalclinic.ai.conversation.model.ChatIntent;
import com.dentalclinic.ai.conversation.model.ChatSemanticContext;
import com.dentalclinic.ai.conversation.model.ChatSessionPreference;
import com.dentalclinic.ai.conversation.service.FallbackConversationUnderstandingService;
import com.dentalclinic.ai.conversation.service.VietnameseRelativeDateResolver;
import com.dentalclinic.common.util.ClinicTimeProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackConversationUnderstandingServiceTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-19T01:00:00Z"),
            CLINIC_ZONE
    );
    private final FallbackConversationUnderstandingService service =
            new FallbackConversationUnderstandingService(
                    new VietnameseRelativeDateResolver(new ClinicTimeProvider(CLOCK))
            );

    @Test
    void shouldPreserveTemporalFollowUpWithoutInventingEntities() {
        ChatSemanticContext previous = new ChatSemanticContext(
                List.of(ChatIntent.DOCTOR_AVAILABLE_SLOTS),
                List.of(),
                new ChatEntityReference(UUID.randomUUID(), "Bác sĩ Demo"),
                null,
                null,
                null,
                null
        );

        var result = service.analyze("Chiều mai thì sao?", previous);

        assertThat(result.intents()).containsExactly(ChatIntent.OUT_OF_SCOPE);
        assertThat(result.followUpType()).isEqualTo(ChatFollowUpType.TEMPORAL_REFINEMENT);
        assertThat(result.entities().date()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(result.entities().sessionPreference())
                .isEqualTo(ChatSessionPreference.AFTERNOON);
        assertThat(result.entities().doctorId()).isNull();
    }

    @Test
    void shouldClassifyAvailabilitySafelyWhenProviderFails() {
        var result = service.analyze(
                "Bác sĩ Demo ngày mai còn lịch không?",
                ChatSemanticContext.empty()
        );

        assertThat(result.intents()).containsExactly(ChatIntent.AVAILABLE_SLOTS);
        assertThat(result.entities().date()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(result.entities().doctorName()).isNull();
    }

    @Test
    void shouldClassifySymptomAndDoctorRecommendationWithoutDiagnosis() {
        var result = service.analyze(
                "Răng bị đau, gợi ý bác sĩ giúp tôi",
                ChatSemanticContext.empty()
        );

        assertThat(result.intents()).containsExactly(
                ChatIntent.SYMPTOM_CONSULTATION,
                ChatIntent.DOCTOR_RECOMMENDATION
        );
        assertThat(result.entities().symptoms()).containsExactly("đau răng");
        assertThat(result.entities().doctorId()).isNull();
    }

    @Test
    void shouldReturnClarifiableDentalIntentForUnclearDentalQuestion() {
        var result = service.analyze(
                "Răng của tôi có vấn đề",
                ChatSemanticContext.empty()
        );

        assertThat(result.intents()).containsExactly(ChatIntent.SYMPTOM_CONSULTATION);
        assertThat(result.entities().symptoms()).isEmpty();
    }

    @Test
    void shouldRecognizeAlternativeDoctorFollowUp() {
        ChatSemanticContext previous = new ChatSemanticContext(
                List.of(ChatIntent.DOCTOR_RECOMMENDATION),
                List.of("đau răng"),
                new ChatEntityReference(UUID.randomUUID(), "Bác sĩ Demo"),
                null,
                null,
                null,
                null
        );

        var result = service.analyze("Bác sĩ khác thì sao?", previous);

        assertThat(result.intents()).containsExactly(ChatIntent.OUT_OF_SCOPE);
        assertThat(result.followUpType()).isEqualTo(ChatFollowUpType.ALTERNATIVE_DOCTOR);
    }

    @Test
    void shouldReturnOutOfScopeForUnrelatedContent() {
        var result = service.analyze(
                "Hôm nay thời tiết thế nào?",
                ChatSemanticContext.empty()
        );

        assertThat(result.intents()).containsExactly(ChatIntent.OUT_OF_SCOPE);
    }
}
