package com.dentalclinic.ai.conversation;

import com.dentalclinic.ai.conversation.model.ChatEntityReference;
import com.dentalclinic.ai.conversation.model.ChatDateRange;
import com.dentalclinic.ai.conversation.model.ChatFollowUpType;
import com.dentalclinic.ai.conversation.model.ChatIntent;
import com.dentalclinic.ai.conversation.model.ChatSemanticContext;
import com.dentalclinic.ai.conversation.model.ChatSessionPreference;
import com.dentalclinic.ai.conversation.service.FallbackConversationUnderstandingService;
import com.dentalclinic.ai.conversation.service.VietnameseRelativeDateResolver;
import com.dentalclinic.common.util.ClinicTimeProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(strings = {
            "Bác sĩ Demo chiều mai còn lịch không?",
            "Khám răng tổng quát giá bao nhiêu?",
            "Tôi bị ê buốt răng, bác sĩ nào phù hợp?",
            "Hôm nay thời tiết thế nào?",
            "Ngày mai giá dịch vụ thế nào?",
            "Ngày mai có bác sĩ nào còn lịch?",
            "Câu hỏi khác, chiều mai thì sao?"
    })
    void shouldNotTreatIndependentQuestionsAsFollowUps(String message) {
        assertThat(service.analyzeFollowUp(message, previousContext())).isEmpty();
        assertThat(service.analyze(message, previousContext()).followUpType()).isEqualTo(ChatFollowUpType.NONE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Ngày mai thì sao?", "Chiều thì còn không?", "Chiều mai còn ca không?",
            "Ngày 20/11/2026 thì sao?", "Tuần sau thì sao?"
    })
    void shouldRecognizeOnlyTemporalFollowUpWithoutCopyingOldReferences(String message) {
        var result = service.analyzeFollowUp(message, previousContext()).orElseThrow();

        assertThat(result.followUpType()).isEqualTo(ChatFollowUpType.TEMPORAL_REFINEMENT);
        assertThat(result.entities().doctorId()).isNull();
        assertThat(result.entities().serviceId()).isNull();
        assertThat(result.entities().symptoms()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bác sĩ nào phù hợp?", "Vậy nên gặp bác sĩ nào?", "Gợi ý bác sĩ giúp tôi"})
    void shouldRecognizeDoctorRecommendationAsContinuationOfClinicalContext(String message) {
        var result = service.analyze(message, previousContext());

        assertThat(result.intents()).containsExactly(ChatIntent.DOCTOR_RECOMMENDATION);
        assertThat(result.followUpType()).isEqualTo(ChatFollowUpType.CONTEXT_CONTINUATION);
        assertThat(result.entities().symptoms()).isEmpty();
        assertThat(result.entities().doctorId()).isNull();
        assertThat(result.entities().date()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Dịch vụ đó giá bao nhiêu?", "Giá của dịch vụ đó là bao nhiêu?"})
    void shouldRecognizeExplicitPriceReference(String message) {
        var result = service.analyze(message, previousContext());

        assertThat(result.intents()).containsExactly(ChatIntent.SERVICE_CATALOG);
        assertThat(result.followUpType()).isEqualTo(ChatFollowUpType.CONTEXT_CONTINUATION);
        assertThat(result.entities().serviceId()).isNull();
    }

    @Test
    void shouldRecognizeAddedSymptomsAsContinuationWithoutCopyingPreviousSymptoms() {
        var result = service.analyze("Ngoài ra tôi còn bị sưng nướu", previousContext());

        assertThat(result.followUpType()).isEqualTo(ChatFollowUpType.CONTEXT_CONTINUATION);
        assertThat(result.entities().symptoms()).containsExactly("sưng nướu");
    }

    @Test
    void shouldAskForClinicalDetailsWhenRecommendationHasNoPreviousSubject() {
        var result = service.analyze("Bác sĩ nào phù hợp?", ChatSemanticContext.empty());

        assertThat(result.followUpType()).isEqualTo(ChatFollowUpType.NONE);
        assertThat(result.entities().symptoms()).isEmpty();
        assertThat(result.entities().doctorId()).isNull();
    }

    private ChatSemanticContext previousContext() {
        return new ChatSemanticContext(
                List.of(ChatIntent.DOCTOR_AVAILABLE_SLOTS), List.of("đau răng"),
                new ChatEntityReference(UUID.randomUUID(), "Bác sĩ Nguyễn Văn A"),
                new ChatEntityReference(UUID.randomUUID(), "Cạo vôi răng"),
                ChatDateRange.single(LocalDate.of(2026, 11, 19)), ChatSessionPreference.MORNING, "08:00"
        );
    }
}
