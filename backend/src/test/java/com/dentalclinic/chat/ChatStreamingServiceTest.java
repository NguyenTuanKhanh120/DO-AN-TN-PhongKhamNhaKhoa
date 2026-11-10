package com.dentalclinic.chat;

import com.dentalclinic.ai.conversation.model.ChatContextResolution;
import com.dentalclinic.ai.conversation.model.ChatFollowUpType;
import com.dentalclinic.ai.conversation.model.ChatIntent;
import com.dentalclinic.ai.conversation.model.ChatSemanticContext;
import com.dentalclinic.ai.conversation.model.ChatSessionPreference;
import com.dentalclinic.ai.gateway.AiStreamingChatGateway;
import com.dentalclinic.ai.orchestration.model.ChatGroundingResult;
import com.dentalclinic.ai.orchestration.model.ChatPrompt;
import com.dentalclinic.ai.orchestration.service.ChatResponseValidator;
import com.dentalclinic.ai.slot.model.ChatSlotSuggestion;
import com.dentalclinic.chat.dto.ChatMessageResponse;
import com.dentalclinic.chat.dto.ChatStreamErrorEvent;
import com.dentalclinic.chat.dto.ChatStreamMetadataEvent;
import com.dentalclinic.chat.dto.SendChatMessageRequest;
import com.dentalclinic.chat.dto.SendChatMessageResponse;
import com.dentalclinic.chat.entity.ChatSender;
import com.dentalclinic.chat.model.ChatTurnStart;
import com.dentalclinic.chat.model.PreparedChatTurn;
import com.dentalclinic.chat.service.ChatOrchestrator;
import com.dentalclinic.chat.service.ChatStreamingService;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.genai.errors.ClientException;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatStreamingServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-29T03:00:00Z");

    private final ChatOrchestrator orchestrator = mock(ChatOrchestrator.class);
    private final AiStreamingChatGateway gateway = mock(AiStreamingChatGateway.class);
    private final UUID userId = UUID.randomUUID();
    private final SendChatMessageRequest request = new SendChatMessageRequest("Tôi bị đau răng");

    @Test
    void shouldStreamStartedTokensMetadataAndCompletedInOrder() {
        PreparedChatTurn prepared = generatedTurn();
        ChatSlotSuggestion appointmentCase = appointmentCase();
        SendChatMessageResponse completed = completedResponse(
                prepared,
                "Xin chào",
                List.of(appointmentCase)
        );
        when(orchestrator.prepareTurn(userId, request)).thenReturn(prepared);
        when(gateway.stream("system", "user")).thenReturn(Flux.just("Xin ", "chào"));
        when(orchestrator.completeTurn(prepared, "Xin chào")).thenReturn(completed);

        List<ServerSentEvent<Object>> events = service(Duration.ofSeconds(1))
                .streamMessage(userId, request)
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(eventNames(events))
                .containsExactly("started", "token", "token", "metadata", "completed");
        assertThat(events.get(3).data())
                .isInstanceOfSatisfying(ChatStreamMetadataEvent.class, metadata ->
                        assertThat(metadata.suggestedSlots()).containsExactly(appointmentCase)
                );
        verify(orchestrator).completeTurn(prepared, "Xin chào");
    }

    @Test
    void shouldStreamDeterministicAnswerWithoutCallingGenerationGateway() {
        PreparedChatTurn prepared = immediateTurn("Bạn muốn tìm lịch của bác sĩ hay dịch vụ nào?");
        SendChatMessageResponse completed = completedResponse(
                prepared,
                prepared.immediateAnswer()
        );
        when(orchestrator.prepareTurn(userId, request)).thenReturn(prepared);
        when(orchestrator.completeTurn(prepared, prepared.immediateAnswer()))
                .thenReturn(completed);

        List<ServerSentEvent<Object>> events = service(Duration.ofSeconds(1))
                .streamMessage(userId, request)
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(eventNames(events))
                .containsExactly("started", "token", "metadata", "completed");
        verify(gateway, never()).stream(any(), any());
    }

    @Test
    void shouldEndWithPartialErrorAndNotPersistIncompleteAnswer() {
        PreparedChatTurn prepared = generatedTurn();
        when(orchestrator.prepareTurn(userId, request)).thenReturn(prepared);
        when(gateway.stream("system", "user")).thenReturn(Flux.concat(
                Flux.just("Nội dung đầu"),
                Flux.error(new IllegalStateException("provider failed"))
        ));

        List<ServerSentEvent<Object>> events = service(Duration.ofSeconds(1))
                .streamMessage(userId, request)
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(eventNames(events)).containsExactly("started", "token", "error");
        assertThat(events.get(2).data())
                .isInstanceOfSatisfying(ChatStreamErrorEvent.class, error -> {
                    assertThat(error.code()).isEqualTo("AI_SERVICE_UNAVAILABLE");
                    assertThat(error.partial()).isTrue();
                });
        verify(orchestrator, never()).completeTurn(any(), any());
    }

    @Test
    void shouldExplainGeminiQuotaFailureWithoutPersistingAssistantMessage() {
        PreparedChatTurn prepared = generatedTurn();
        when(orchestrator.prepareTurn(userId, request)).thenReturn(prepared);
        when(gateway.stream("system", "user")).thenReturn(Flux.error(
                new ClientException(429, "RESOURCE_EXHAUSTED", "quota exceeded")
        ));

        List<ServerSentEvent<Object>> events = service(Duration.ofSeconds(1))
                .streamMessage(userId, request)
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(eventNames(events)).containsExactly("started", "error");
        assertThat(events.get(1).data())
                .isInstanceOfSatisfying(ChatStreamErrorEvent.class, error -> {
                    assertThat(error.message())
                            .isEqualTo("Trợ lý AI đã đạt giới hạn sử dụng Gemini. Vui lòng thử lại sau.");
                    assertThat(error.partial()).isFalse();
                });
        verify(orchestrator, never()).completeTurn(any(), any());
    }

    @Test
    void shouldTimeoutSilentProviderWithoutPersistingAssistantMessage() {
        PreparedChatTurn prepared = generatedTurn();
        when(orchestrator.prepareTurn(userId, request)).thenReturn(prepared);
        when(gateway.stream("system", "user")).thenReturn(Flux.never());

        List<ServerSentEvent<Object>> events = service(Duration.ofMillis(25))
                .streamMessage(userId, request)
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(eventNames(events)).containsExactly("started", "error");
        assertThat(events.get(1).data())
                .isInstanceOfSatisfying(ChatStreamErrorEvent.class, error -> {
                    assertThat(error.message()).contains("quá thời gian chờ");
                    assertThat(error.partial()).isFalse();
                });
        verify(orchestrator, never()).completeTurn(any(), any());
    }

    @Test
    void shouldCancelProviderAndAvoidPersistenceWhenClientDisconnects() {
        PreparedChatTurn prepared = generatedTurn();
        when(orchestrator.prepareTurn(userId, request)).thenReturn(prepared);
        when(gateway.stream("system", "user")).thenReturn(Flux.concat(
                Flux.just("Một phần"),
                Flux.never()
        ));

        List<ServerSentEvent<Object>> received = service(Duration.ofSeconds(5))
                .streamMessage(userId, request)
                .take(2)
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(eventNames(received)).containsExactly("started", "token");
        verify(orchestrator, never()).completeTurn(any(), any());
    }

    @Test
    void shouldReturnSafeErrorBeforePersistenceWhenStreamingIsDisabled() {
        ChatStreamingService service = new ChatStreamingService(
                orchestrator,
                new ChatResponseValidator(),
                Optional.empty(),
                Duration.ofSeconds(1)
        );

        List<ServerSentEvent<Object>> events = service.streamMessage(userId, request)
                .collectList()
                .block(Duration.ofSeconds(1));

        assertThat(eventNames(events)).containsExactly("error");
        assertThat(events.getFirst().data()).isInstanceOf(ChatStreamErrorEvent.class);
        verify(orchestrator, never()).prepareTurn(any(), any());
    }

    private ChatStreamingService service(Duration timeout) {
        return new ChatStreamingService(
                orchestrator,
                new ChatResponseValidator(),
                Optional.of(gateway),
                timeout
        );
    }

    private PreparedChatTurn generatedTurn() {
        ChatSemanticContext context = context();
        return new PreparedChatTurn(
                userId,
                new ChatTurnStart(userMessage(), JsonNodeFactory.instance.objectNode()),
                resolution(context),
                ChatGroundingResult.empty(context),
                new ChatPrompt("system", "user"),
                null
        );
    }

    private PreparedChatTurn immediateTurn(String answer) {
        ChatSemanticContext context = context();
        return new PreparedChatTurn(
                userId,
                new ChatTurnStart(userMessage(), JsonNodeFactory.instance.objectNode()),
                resolution(context),
                ChatGroundingResult.empty(context),
                null,
                answer
        );
    }

    private ChatContextResolution resolution(ChatSemanticContext context) {
        return new ChatContextResolution(
                context,
                false,
                List.of(),
                null,
                ChatFollowUpType.NONE
        );
    }

    private ChatSemanticContext context() {
        return new ChatSemanticContext(
                List.of(ChatIntent.SYMPTOM_CONSULTATION),
                List.of("đau răng"),
                null,
                null,
                null,
                null,
                null
        );
    }

    private ChatMessageResponse userMessage() {
        return new ChatMessageResponse(
                UUID.randomUUID(),
                ChatSender.USER,
                request.message(),
                JsonNodeFactory.instance.objectNode(),
                NOW
        );
    }

    private SendChatMessageResponse completedResponse(PreparedChatTurn prepared, String answer) {
        return completedResponse(prepared, answer, List.of());
    }

    private SendChatMessageResponse completedResponse(
            PreparedChatTurn prepared,
            String answer,
            List<ChatSlotSuggestion> suggestedCases
    ) {
        return new SendChatMessageResponse(
                prepared.turn().userMessage().id(),
                UUID.randomUUID(),
                answer,
                prepared.grounding().context().lastIntents(),
                List.of(),
                List.of(),
                List.of(),
                suggestedCases,
                false,
                NOW
        );
    }

    private ChatSlotSuggestion appointmentCase() {
        return new ChatSlotSuggestion(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 11, 10),
                ChatSessionPreference.AFTERNOON,
                1,
                LocalTime.of(14, 0),
                LocalTime.of(17, 0),
                LocalTime.of(14, 0),
                OffsetDateTime.parse("2026-11-10T14:00:00+07:00")
        );
    }

    private List<String> eventNames(List<ServerSentEvent<Object>> events) {
        return events.stream().map(ServerSentEvent::event).toList();
    }
}
