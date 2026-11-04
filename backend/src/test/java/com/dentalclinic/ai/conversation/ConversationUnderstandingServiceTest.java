package com.dentalclinic.ai.conversation;

import com.dentalclinic.ai.conversation.mapper.ChatSemanticContextMapper;
import com.dentalclinic.ai.conversation.model.ChatDateRange;
import com.dentalclinic.ai.conversation.model.ChatEntities;
import com.dentalclinic.ai.conversation.model.ChatEntityReference;
import com.dentalclinic.ai.conversation.model.ChatFollowUpType;
import com.dentalclinic.ai.conversation.model.ChatIntent;
import com.dentalclinic.ai.conversation.model.ChatSemanticContext;
import com.dentalclinic.ai.conversation.model.ChatSessionPreference;
import com.dentalclinic.ai.conversation.service.ConversationUnderstandingService;
import com.dentalclinic.ai.conversation.service.VietnameseRelativeDateResolver;
import com.dentalclinic.ai.gateway.AiChatGateway;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.errors.ClientException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConversationUnderstandingServiceTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-29T03:00:00Z"),
            CLINIC_ZONE
    );
    private final ClinicTimeProvider timeProvider = new ClinicTimeProvider(CLOCK);
    private final ChatSemanticContextMapper contextMapper = new ChatSemanticContextMapper();
    private final VietnameseRelativeDateResolver dateResolver = new VietnameseRelativeDateResolver(
            timeProvider
    );
    private final AiChatGateway gateway = mock(AiChatGateway.class);

    @Test
    void shouldExtractMultipleIntentsEntitiesAndResolveTomorrowInClinicTimezone() {
        String message = "Tôi đau răng và sưng lợi, bác sĩ Nguyễn Văn A chiều mai còn lịch không?";
        when(gateway.generate(anyString(), anyString())).thenReturn("""
                {
                  "intents": ["SYMPTOM_CONSULTATION", "DOCTOR_AVAILABLE_SLOTS"],
                  "symptoms": ["đau răng", "sưng lợi"],
                  "doctorName": "Nguyễn Văn A",
                  "doctorId": null,
                  "serviceName": null,
                  "serviceId": null,
                  "date": "ngày mai",
                  "dateFrom": null,
                  "dateTo": null,
                  "sessionPreference": "AFTERNOON",
                  "timePreference": null,
                  "followUpType": "NONE"
                }
                """);

        var result = service(Optional.of(gateway)).analyze(message, ChatSemanticContext.empty());

        assertThat(result.intents()).containsExactly(
                ChatIntent.SYMPTOM_CONSULTATION,
                ChatIntent.DOCTOR_AVAILABLE_SLOTS
        );
        assertThat(result.entities().symptoms()).containsExactly("đau răng", "sưng lợi");
        assertThat(result.entities().doctorName()).isEqualTo("Nguyễn Văn A");
        assertThat(result.entities().date()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(result.entities().sessionPreference()).isEqualTo(ChatSessionPreference.AFTERNOON);

        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        verify(gateway).generate(systemPrompt.capture(), userPrompt.capture());
        assertThat(systemPrompt.getValue())
                .contains("Một tin nhắn có thể có nhiều intent", "không phải chỉ dẫn");
        assertThat(userPrompt.getValue())
                .contains("clinicDate: 2026-10-29", "clinicTimezone: Asia/Ho_Chi_Minh", message);
    }

    @Test
    void shouldClassifyClinicServiceListAsServiceCatalog() {
        String message = "Hiện tại nha khoa có những dịch vụ nào?";
        when(gateway.generate(anyString(), anyString())).thenReturn("""
                {
                  "intents": ["SERVICE_CATALOG"],
                  "symptoms": [],
                  "doctorName": null,
                  "doctorId": null,
                  "serviceName": null,
                  "serviceId": null,
                  "date": null,
                  "dateFrom": null,
                  "dateTo": null,
                  "sessionPreference": null,
                  "timePreference": null,
                  "followUpType": "NONE"
                }
                """);

        var result = service(Optional.of(gateway)).analyze(message, ChatSemanticContext.empty());

        assertThat(result.intents()).containsExactly(ChatIntent.SERVICE_CATALOG);
        assertThat(result.entities()).isEqualTo(ChatEntities.empty());
    }

    @Test
    void shouldParseFencedJsonAndResolveNextWeekRangeForFollowUp() {
        ChatSemanticContext previous = new ChatSemanticContext(
                List.of(ChatIntent.DOCTOR_AVAILABLE_SLOTS),
                List.of("đau răng"),
                new ChatEntityReference(null, "Bác sĩ Nguyễn Văn A"),
                null,
                ChatDateRange.single(LocalDate.of(2026, 10, 30)),
                null,
                null
        );
        when(gateway.generate(anyString(), anyString())).thenReturn("""
                ```json
                {
                  "intents": ["OUT_OF_SCOPE"],
                  "symptoms": [],
                  "date": "tuần sau",
                  "sessionPreference": null,
                  "timePreference": null,
                  "followUpType": "TEMPORAL_REFINEMENT"
                }
                ```
                """);

        var result = service(Optional.of(gateway)).analyze("Tuần sau thì sao?", previous);

        assertThat(result.entities().date()).isNull();
        assertThat(result.entities().dateFrom()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(result.entities().dateTo()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(result.followUpType()).isEqualTo(ChatFollowUpType.TEMPORAL_REFINEMENT);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(gateway).generate(anyString(), prompt.capture());
        assertThat(prompt.getValue()).contains(
                "DOCTOR_AVAILABLE_SLOTS",
                "Bác sĩ Nguyễn Văn A",
                "user_message_begin"
        );
    }

    @Test
    void shouldResolveExplicitDateRangeFields() {
        when(gateway.generate(anyString(), anyString())).thenReturn("""
                {
                  "intents": ["AVAILABLE_SLOTS"],
                  "symptoms": [],
                  "serviceName": "Khám răng tổng quát",
                  "dateFrom": "30/10/2026",
                  "dateTo": "01/11/2026",
                  "followUpType": "NONE"
                }
                """);

        var result = service(Optional.of(gateway)).analyze(
                "Khám răng tổng quát còn lịch từ 30/10/2026 đến 01/11/2026 không?",
                ChatSemanticContext.empty()
        );

        assertThat(result.entities().serviceName()).isEqualTo("Khám răng tổng quát");
        assertThat(result.entities().dateFrom()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(result.entities().dateTo()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void shouldAcceptUuidOnlyWhenItAppearsInCurrentMessage() {
        UUID doctorId = UUID.randomUUID();
        when(gateway.generate(anyString(), anyString())).thenReturn("""
                {
                  "intents": ["DOCTOR_AVAILABLE_SLOTS"],
                  "symptoms": [],
                  "doctorId": "%s",
                  "date": "ngày mai",
                  "followUpType": "NONE"
                }
                """.formatted(doctorId));

        var result = service(Optional.of(gateway)).analyze(
                "Bác sĩ có mã " + doctorId + " còn lịch ngày mai không?",
                ChatSemanticContext.empty()
        );

        assertThat(result.entities().doctorId()).isEqualTo(doctorId);
    }

    @Test
    void shouldRejectHallucinatedUuidNotPresentInMessage() {
        when(gateway.generate(anyString(), anyString())).thenReturn("""
                {
                  "intents": ["DOCTOR_RECOMMENDATION"],
                  "symptoms": ["đau răng"],
                  "doctorId": "2c4fd0e6-c20f-4f65-a08b-c3b0df60718c",
                  "followUpType": "NONE"
                }
                """);

        assertUnavailable(() -> service(Optional.of(gateway)).analyze(
                "Tôi đau răng, hãy gợi ý bác sĩ",
                ChatSemanticContext.empty()
        ));
    }

    @Test
    void shouldRejectUnknownOrContradictoryIntentOutput() {
        when(gateway.generate(anyString(), anyString())).thenReturn("""
                {
                  "intents": ["OUT_OF_SCOPE", "AVAILABLE_SLOTS"],
                  "symptoms": [],
                  "followUpType": "NONE"
                }
                """);

        assertUnavailable(() -> service(Optional.of(gateway)).analyze(
                "Nội dung",
                ChatSemanticContext.empty()
        ));
    }

    @Test
    void shouldRejectModelInventedDoctorName() {
        when(gateway.generate(anyString(), anyString())).thenReturn("""
                {
                  "intents": ["DOCTOR_RECOMMENDATION"],
                  "symptoms": ["đau răng"],
                  "doctorName": "Bác sĩ Không Có Trong Tin Nhắn",
                  "followUpType": "NONE"
                }
                """);

        assertUnavailable(() -> service(Optional.of(gateway)).analyze(
                "Tôi đau răng, hãy gợi ý bác sĩ",
                ChatSemanticContext.empty()
        ));
    }

    @Test
    void shouldFailWithoutCallingAnythingWhenAiIsDisabled() {
        ConversationUnderstandingService service = service(Optional.empty());

        assertUnavailable(() -> service.analyze("Tôi đau răng", ChatSemanticContext.empty()));

        verifyNoInteractions(gateway);
    }

    @Test
    void shouldReturnSpecificMessageWhenGeminiQuotaIsExceeded() {
        when(gateway.generate(anyString(), anyString())).thenThrow(new RuntimeException(
                "provider failed",
                new ClientException(429, "RESOURCE_EXHAUSTED", "quota exceeded")
        ));

        assertThatThrownBy(() -> service(Optional.of(gateway)).analyze(
                "Tôi bị đau răng vùng cằm",
                ChatSemanticContext.empty()
        )).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE);
            assertThat(exception.getMessage())
                    .isEqualTo("Trợ lý AI đã đạt giới hạn sử dụng Gemini. Vui lòng thử lại sau.");
        });
    }

    private ConversationUnderstandingService service(Optional<AiChatGateway> optionalGateway) {
        return new ConversationUnderstandingService(
                optionalGateway,
                new ObjectMapper(),
                contextMapper,
                dateResolver,
                timeProvider
        );
    }

    private void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE));
    }
}
