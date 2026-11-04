package com.dentalclinic.ai.conversation;

import com.dentalclinic.ai.conversation.model.ChatDateRange;
import com.dentalclinic.ai.conversation.model.ChatEntities;
import com.dentalclinic.ai.conversation.model.ChatEntityReference;
import com.dentalclinic.ai.conversation.model.ChatFollowUpType;
import com.dentalclinic.ai.conversation.model.ChatIntent;
import com.dentalclinic.ai.conversation.model.ChatSemanticContext;
import com.dentalclinic.ai.conversation.model.ChatSessionPreference;
import com.dentalclinic.ai.conversation.model.ChatUnderstanding;
import com.dentalclinic.ai.conversation.model.ClarificationField;
import com.dentalclinic.ai.conversation.service.ChatContextResolver;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatContextResolverTest {

    private final ChatContextResolver resolver = new ChatContextResolver();

    @Test
    void shouldMergeMultiIntentSymptomsAndTemporalEntities() {
        ChatUnderstanding understanding = new ChatUnderstanding(
                List.of(ChatIntent.SYMPTOM_CONSULTATION, ChatIntent.DOCTOR_RECOMMENDATION),
                entities(
                        List.of("đau răng", "sưng lợi"),
                        null,
                        null,
                        LocalDate.of(2026, 10, 30),
                        null,
                        null,
                        ChatSessionPreference.AFTERNOON
                ),
                ChatFollowUpType.NONE
        );

        var result = resolver.resolve(ChatSemanticContext.empty(), understanding);

        assertThat(result.context().lastIntents()).containsExactly(
                ChatIntent.SYMPTOM_CONSULTATION,
                ChatIntent.DOCTOR_RECOMMENDATION
        );
        assertThat(result.context().symptoms()).containsExactly("đau răng", "sưng lợi");
        assertThat(result.context().dateRange())
                .isEqualTo(ChatDateRange.single(LocalDate.of(2026, 10, 30)));
        assertThat(result.context().sessionPreference()).isEqualTo(ChatSessionPreference.AFTERNOON);
        assertThat(result.needsClarification()).isFalse();
    }

    @Test
    void shouldCarryPreviousIntentAndDoctorForDateOnlyFollowUp() {
        ChatSemanticContext previous = previousSlotContext();
        ChatUnderstanding followUp = new ChatUnderstanding(
                List.of(ChatIntent.OUT_OF_SCOPE),
                entities(
                        List.of(),
                        null,
                        null,
                        LocalDate.of(2026, 10, 31),
                        null,
                        null,
                        null
                ),
                ChatFollowUpType.TEMPORAL_REFINEMENT
        );

        var result = resolver.resolve(previous, followUp);

        assertThat(result.context().lastIntents()).containsExactly(ChatIntent.DOCTOR_AVAILABLE_SLOTS);
        assertThat(result.context().selectedDoctor()).isEqualTo(previous.selectedDoctor());
        assertThat(result.context().dateRange())
                .isEqualTo(ChatDateRange.single(LocalDate.of(2026, 10, 31)));
        assertThat(result.needsClarification()).isFalse();
    }

    @Test
    void shouldCarryDateAndDoctorForAfternoonOnlyFollowUp() {
        ChatUnderstanding followUp = new ChatUnderstanding(
                List.of(ChatIntent.OUT_OF_SCOPE),
                entities(
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        ChatSessionPreference.AFTERNOON
                ),
                ChatFollowUpType.TEMPORAL_REFINEMENT
        );

        var result = resolver.resolve(previousSlotContext(), followUp);

        assertThat(result.context().lastIntents()).containsExactly(ChatIntent.DOCTOR_AVAILABLE_SLOTS);
        assertThat(result.context().sessionPreference()).isEqualTo(ChatSessionPreference.AFTERNOON);
        assertThat(result.context().dateRange())
                .isEqualTo(ChatDateRange.single(LocalDate.of(2026, 10, 30)));
    }

    @Test
    void shouldPreserveSelectedDoctorForAlternativeDoctorFollowUp() {
        ChatUnderstanding followUp = new ChatUnderstanding(
                List.of(ChatIntent.OUT_OF_SCOPE),
                ChatEntities.empty(),
                ChatFollowUpType.ALTERNATIVE_DOCTOR
        );

        var result = resolver.resolve(previousSlotContext(), followUp);

        assertThat(result.context().selectedDoctor()).isEqualTo(previousSlotContext().selectedDoctor());
        assertThat(result.followUpType()).isEqualTo(ChatFollowUpType.ALTERNATIVE_DOCTOR);
    }

    @Test
    void shouldRequestDoctorAndDateForIncompleteDoctorSlotIntent() {
        ChatUnderstanding understanding = new ChatUnderstanding(
                List.of(ChatIntent.DOCTOR_AVAILABLE_SLOTS),
                ChatEntities.empty(),
                ChatFollowUpType.NONE
        );

        var result = resolver.resolve(ChatSemanticContext.empty(), understanding);

        assertThat(result.needsClarification()).isTrue();
        assertThat(result.missingFields()).containsExactly(
                ClarificationField.DOCTOR,
                ClarificationField.DATE
        );
        assertThat(result.clarificationQuestion())
                .contains("bác sĩ bạn muốn khám", "ngày hoặc khoảng ngày");
    }

    @Test
    void shouldRequestCompleteDateWhenOnlyDateFromWasExtracted() {
        ChatUnderstanding understanding = new ChatUnderstanding(
                List.of(ChatIntent.AVAILABLE_SLOTS),
                entities(
                        List.of(),
                        null,
                        "Khám răng tổng quát",
                        null,
                        LocalDate.of(2026, 11, 2),
                        null,
                        null
                ),
                ChatFollowUpType.NONE
        );

        var result = resolver.resolve(ChatSemanticContext.empty(), understanding);

        assertThat(result.missingFields()).containsExactly(ClarificationField.DATE);
        assertThat(result.context().selectedService().name()).isEqualTo("Khám răng tổng quát");
    }

    @Test
    void shouldRequestSymptomsForServiceRecommendationWithoutSymptoms() {
        ChatUnderstanding understanding = new ChatUnderstanding(
                List.of(ChatIntent.SERVICE_RECOMMENDATION),
                ChatEntities.empty(),
                ChatFollowUpType.NONE
        );

        var result = resolver.resolve(ChatSemanticContext.empty(), understanding);

        assertThat(result.missingFields()).containsExactly(ClarificationField.SYMPTOMS);
    }

    private ChatSemanticContext previousSlotContext() {
        return new ChatSemanticContext(
                List.of(ChatIntent.DOCTOR_AVAILABLE_SLOTS),
                List.of("đau răng"),
                new ChatEntityReference(null, "Bác sĩ Nguyễn Văn A"),
                new ChatEntityReference(null, "Khám răng tổng quát"),
                ChatDateRange.single(LocalDate.of(2026, 10, 30)),
                ChatSessionPreference.MORNING,
                null
        );
    }

    private ChatEntities entities(
            List<String> symptoms,
            String doctorName,
            String serviceName,
            LocalDate date,
            LocalDate dateFrom,
            LocalDate dateTo,
            ChatSessionPreference sessionPreference
    ) {
        return new ChatEntities(
                symptoms,
                doctorName,
                null,
                serviceName,
                null,
                date,
                dateFrom,
                dateTo,
                sessionPreference,
                null
        );
    }
}
