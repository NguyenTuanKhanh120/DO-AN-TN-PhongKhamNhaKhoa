package com.dentalclinic.chat;

import com.dentalclinic.ai.conversation.mapper.ChatSemanticContextMapper;
import com.dentalclinic.ai.conversation.model.ChatContextResolution;
import com.dentalclinic.ai.conversation.model.ChatEntities;
import com.dentalclinic.ai.conversation.model.ChatFollowUpType;
import com.dentalclinic.ai.conversation.model.ChatIntent;
import com.dentalclinic.ai.conversation.model.ChatSemanticContext;
import com.dentalclinic.ai.conversation.model.ChatUnderstanding;
import com.dentalclinic.ai.conversation.model.ClarificationField;
import com.dentalclinic.ai.conversation.service.ChatContextResolver;
import com.dentalclinic.ai.conversation.service.ConversationUnderstandingService;
import com.dentalclinic.ai.gateway.AiChatGateway;
import com.dentalclinic.ai.orchestration.model.ChatGroundingResult;
import com.dentalclinic.ai.orchestration.model.ChatPrompt;
import com.dentalclinic.ai.orchestration.service.ChatGroundingService;
import com.dentalclinic.ai.orchestration.service.ChatPromptBuilder;
import com.dentalclinic.ai.orchestration.service.ChatResponseValidator;
import com.dentalclinic.chat.dto.ChatMessageResponse;
import com.dentalclinic.chat.dto.SendChatMessageRequest;
import com.dentalclinic.chat.entity.ChatSender;
import com.dentalclinic.chat.model.ChatTurnStart;
import com.dentalclinic.chat.service.ChatOrchestrator;
import com.dentalclinic.chat.service.ChatPersistenceService;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.service.dto.DentalServiceResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.io.InterruptedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatOrchestratorTest {

    private static final Instant NOW = Instant.parse("2026-10-29T03:00:00Z");

    private final ChatPersistenceService persistenceService = mock(ChatPersistenceService.class);
    private final ConversationUnderstandingService understandingService =
            mock(ConversationUnderstandingService.class);
    private final ChatContextResolver contextResolver = mock(ChatContextResolver.class);
    private final ChatGroundingService groundingService = mock(ChatGroundingService.class);
    private final ChatPromptBuilder promptBuilder = mock(ChatPromptBuilder.class);
    private final AiChatGateway gateway = mock(AiChatGateway.class);
    private final ChatSemanticContextMapper contextMapper = new ChatSemanticContextMapper();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldOrchestrateUnderstandingGroundingGeminiValidationAndPersistence() {
        UUID userId = UUID.randomUUID();
        String message = "Tôi đau răng, chiều mai bác sĩ nào còn lịch?";
        ChatMessageResponse userMessage = message(ChatSender.USER, message);
        ChatMessageResponse assistantMessage = message(
                ChatSender.ASSISTANT,
                "Tôi đã tìm thấy các gợi ý phù hợp bên dưới."
        );
        ChatSemanticContext previous = ChatSemanticContext.empty();
        ChatSemanticContext resolved = new ChatSemanticContext(
                List.of(
                        ChatIntent.SYMPTOM_CONSULTATION,
                        ChatIntent.DOCTOR_RECOMMENDATION,
                        ChatIntent.AVAILABLE_SLOTS
                ),
                List.of("đau răng"),
                null,
                null,
                null,
                null,
                null
        );
        ChatUnderstanding understanding = new ChatUnderstanding(
                resolved.lastIntents(),
                ChatEntities.empty(),
                ChatFollowUpType.NONE
        );
        ChatContextResolution resolution = new ChatContextResolution(
                resolved,
                false,
                List.of(),
                null,
                ChatFollowUpType.NONE
        );
        ChatGroundingResult grounding = ChatGroundingResult.empty(resolved);

        when(persistenceService.startTurn(userId, message)).thenReturn(new ChatTurnStart(
                userMessage,
                contextMapper.toJson(previous)
        ));
        when(understandingService.analyze(message, previous)).thenReturn(understanding);
        when(contextResolver.resolve(previous, understanding)).thenReturn(resolution);
        when(groundingService.resolve(message, resolution)).thenReturn(grounding);
        when(promptBuilder.build(message, grounding)).thenReturn(new ChatPrompt(
                "System prompt an toàn",
                "Dữ liệu đã được xác minh"
        ));
        when(gateway.generate("System prompt an toàn", "Dữ liệu đã được xác minh"))
                .thenReturn("  " + assistantMessage.content() + "  ");
        when(persistenceService.completeTurn(
                eq(userId),
                eq(assistantMessage.content()),
                any(JsonNode.class),
                any(JsonNode.class)
        )).thenReturn(assistantMessage);

        var response = orchestrator(Optional.of(gateway)).sendMessage(
                userId,
                new SendChatMessageRequest(message)
        );

        assertThat(response.userMessageId()).isEqualTo(userMessage.id());
        assertThat(response.messageId()).isEqualTo(assistantMessage.id());
        assertThat(response.answer()).isEqualTo(assistantMessage.content());
        assertThat(response.intents()).containsExactlyElementsOf(resolved.lastIntents());
        assertThat(response.needsClarification()).isFalse();
        verify(persistenceService).completeTurn(
                eq(userId),
                eq(assistantMessage.content()),
                any(JsonNode.class),
                any(JsonNode.class)
        );
    }

    @Test
    void shouldPersistClarificationWithoutCallingGroundingOrAnswerModel() {
        UUID userId = UUID.randomUUID();
        String message = "Chiều thì còn không?";
        ChatMessageResponse userMessage = message(ChatSender.USER, message);
        String question = "Bạn vui lòng cho biết dịch vụ hoặc bác sĩ bạn muốn tìm lịch.";
        ChatMessageResponse assistantMessage = message(ChatSender.ASSISTANT, question);
        ChatSemanticContext previous = ChatSemanticContext.empty();
        ChatUnderstanding understanding = new ChatUnderstanding(
                List.of(ChatIntent.AVAILABLE_SLOTS),
                ChatEntities.empty(),
                ChatFollowUpType.TEMPORAL_REFINEMENT
        );
        ChatContextResolution resolution = new ChatContextResolution(
                new ChatSemanticContext(
                        List.of(ChatIntent.AVAILABLE_SLOTS),
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                true,
                List.of(ClarificationField.SERVICE_OR_DOCTOR),
                question,
                ChatFollowUpType.TEMPORAL_REFINEMENT
        );

        when(persistenceService.startTurn(userId, message)).thenReturn(new ChatTurnStart(
                userMessage,
                JsonNodeFactory.instance.objectNode()
        ));
        when(understandingService.analyze(message, previous)).thenReturn(understanding);
        when(contextResolver.resolve(previous, understanding)).thenReturn(resolution);
        when(persistenceService.completeTurn(
                eq(userId),
                eq(question),
                any(JsonNode.class),
                any(JsonNode.class)
        )).thenReturn(assistantMessage);

        var response = orchestrator(Optional.of(gateway)).sendMessage(
                userId,
                new SendChatMessageRequest(message)
        );

        assertThat(response.needsClarification()).isTrue();
        assertThat(response.answer()).isEqualTo(question);
        verify(groundingService, never()).resolve(any(), any());
        verify(gateway, never()).generate(any(), any());
    }

    @Test
    void shouldUseInsufficientKnowledgeFallbackWithoutCallingModel() {
        UUID userId = UUID.randomUUID();
        String message = "Tình trạng nha khoa này có nguy hiểm không?";
        ChatMessageResponse userMessage = message(ChatSender.USER, message);
        String safeFallback = "Kho kiến thức hiện chưa có đủ dữ liệu đáng tin cậy để trả lời "
                + "nội dung này. Bạn nên đến phòng khám để được bác sĩ đánh giá trực tiếp.";
        ChatMessageResponse assistantMessage = message(ChatSender.ASSISTANT, safeFallback);
        ChatSemanticContext context = new ChatSemanticContext(
                List.of(ChatIntent.SYMPTOM_CONSULTATION),
                List.of("triệu chứng chưa xác định"),
                null,
                null,
                null,
                null,
                null
        );
        ChatUnderstanding understanding = new ChatUnderstanding(
                context.lastIntents(),
                ChatEntities.empty(),
                ChatFollowUpType.NONE
        );
        ChatContextResolution resolution = new ChatContextResolution(
                context,
                false,
                List.of(),
                null,
                ChatFollowUpType.NONE
        );
        ChatGroundingResult noKnowledge = new ChatGroundingResult(
                context,
                true,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        when(persistenceService.startTurn(userId, message)).thenReturn(new ChatTurnStart(
                userMessage,
                contextMapper.toJson(ChatSemanticContext.empty())
        ));
        when(understandingService.analyze(message, ChatSemanticContext.empty()))
                .thenReturn(understanding);
        when(contextResolver.resolve(ChatSemanticContext.empty(), understanding))
                .thenReturn(resolution);
        when(groundingService.resolve(message, resolution)).thenReturn(noKnowledge);
        when(persistenceService.completeTurn(
                eq(userId),
                eq(safeFallback),
                any(JsonNode.class),
                any(JsonNode.class)
        )).thenReturn(assistantMessage);

        var response = orchestrator(Optional.of(gateway)).sendMessage(
                userId,
                new SendChatMessageRequest(message)
        );

        assertThat(response.answer()).isEqualTo(safeFallback);
        assertThat(response.sources()).isEmpty();
        assertThat(response.suggestedServices()).isEmpty();
        assertThat(response.suggestedDoctors()).isEmpty();
        assertThat(response.suggestedSlots()).isEmpty();
        verify(promptBuilder, never()).build(any(), any());
        verify(gateway, never()).generate(any(), any());
    }

    @Test
    void shouldReturnVerifiedServiceCatalogWithoutCallingAnswerModel() {
        UUID userId = UUID.randomUUID();
        String message = "Hiện tại nha khoa có những dịch vụ nào?";
        ChatSemanticContext previous = ChatSemanticContext.empty();
        ChatSemanticContext context = new ChatSemanticContext(
                List.of(ChatIntent.SERVICE_CATALOG),
                List.of(),
                null,
                null,
                null,
                null,
                null
        );
        ChatUnderstanding understanding = new ChatUnderstanding(
                context.lastIntents(),
                ChatEntities.empty(),
                ChatFollowUpType.NONE
        );
        ChatContextResolution resolution = new ChatContextResolution(
                context,
                false,
                List.of(),
                null,
                ChatFollowUpType.NONE
        );
        DentalServiceResponse cleaning = dentalService("Cạo vôi răng");
        DentalServiceResponse generalExamination = dentalService("Khám răng tổng quát");
        ChatGroundingResult grounding = new ChatGroundingResult(
                context,
                false,
                true,
                List.of(),
                List.of(cleaning, generalExamination),
                List.of(),
                List.of()
        );
        String answer = """
                Hiện tại Nha Khoa An Tâm có các dịch vụ đang hoạt động:
                - Cạo vôi răng
                - Khám răng tổng quát
                Bạn có thể xem thông tin và mức giá ở các thẻ dịch vụ bên dưới.""";
        ChatMessageResponse assistantMessage = message(ChatSender.ASSISTANT, answer);

        when(persistenceService.startTurn(userId, message)).thenReturn(new ChatTurnStart(
                message(ChatSender.USER, message),
                contextMapper.toJson(previous)
        ));
        when(understandingService.analyze(message, previous)).thenReturn(understanding);
        when(contextResolver.resolve(previous, understanding)).thenReturn(resolution);
        when(groundingService.resolve(message, resolution)).thenReturn(grounding);
        when(persistenceService.completeTurn(
                eq(userId),
                eq(answer),
                any(JsonNode.class),
                any(JsonNode.class)
        )).thenReturn(assistantMessage);

        var response = orchestrator(Optional.of(gateway)).sendMessage(
                userId,
                new SendChatMessageRequest(message)
        );

        assertThat(response.answer()).isEqualTo(answer);
        assertThat(response.suggestedServices()).containsExactly(cleaning, generalExamination);
        verify(promptBuilder, never()).build(any(), any());
        verify(gateway, never()).generate(any(), any());
    }

    @Test
    void shouldRejectBeforePersistenceWhenAiIsDisabled() {
        ChatOrchestrator orchestrator = orchestrator(Optional.empty());

        assertThatThrownBy(() -> orchestrator.sendMessage(
                UUID.randomUUID(),
                new SendChatMessageRequest("Xin chào")
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE)
        );

        verify(persistenceService, never()).startTurn(any(), any());
    }

    @Test
    void shouldKeepUserMessageAndNotCompleteTurnWhenAnswerModelFails() {
        UUID userId = UUID.randomUUID();
        String message = "Phòng khám hỗ trợ gì?";
        ChatSemanticContext previous = ChatSemanticContext.empty();
        ChatSemanticContext resolved = new ChatSemanticContext(
                List.of(ChatIntent.CLINIC_INFORMATION),
                List.of(),
                null,
                null,
                null,
                null,
                null
        );
        ChatUnderstanding understanding = new ChatUnderstanding(
                resolved.lastIntents(),
                ChatEntities.empty(),
                ChatFollowUpType.NONE
        );
        ChatContextResolution resolution = new ChatContextResolution(
                resolved,
                false,
                List.of(),
                null,
                ChatFollowUpType.NONE
        );
        ChatGroundingResult grounding = ChatGroundingResult.empty(resolved);
        when(persistenceService.startTurn(userId, message)).thenReturn(new ChatTurnStart(
                message(ChatSender.USER, message),
                contextMapper.toJson(previous)
        ));
        when(understandingService.analyze(message, previous)).thenReturn(understanding);
        when(contextResolver.resolve(previous, understanding)).thenReturn(resolution);
        when(groundingService.resolve(message, resolution)).thenReturn(grounding);
        when(promptBuilder.build(message, grounding)).thenReturn(new ChatPrompt(
                "System prompt an toàn",
                "Dữ liệu đã được xác minh"
        ));
        when(gateway.generate(any(), any())).thenThrow(new IllegalStateException("provider failure"));

        assertThatThrownBy(() -> orchestrator(Optional.of(gateway)).sendMessage(
                userId,
                new SendChatMessageRequest(message)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE)
        );

        verify(persistenceService, never()).completeTurn(any(), any(), any(), any());
    }

    @Test
    void shouldReturnFriendlyUnavailableErrorWhenAnswerModelTimesOut() {
        UUID userId = UUID.randomUUID();
        String message = "Tôi bị đau răng";
        ChatSemanticContext previous = ChatSemanticContext.empty();
        ChatSemanticContext resolved = new ChatSemanticContext(
                List.of(ChatIntent.SYMPTOM_CONSULTATION),
                List.of("đau răng"),
                null,
                null,
                null,
                null,
                null
        );
        ChatUnderstanding understanding = new ChatUnderstanding(
                resolved.lastIntents(),
                ChatEntities.empty(),
                ChatFollowUpType.NONE
        );
        ChatContextResolution resolution = new ChatContextResolution(
                resolved,
                false,
                List.of(),
                null,
                ChatFollowUpType.NONE
        );
        ChatGroundingResult grounding = ChatGroundingResult.empty(resolved);
        when(persistenceService.startTurn(userId, message)).thenReturn(new ChatTurnStart(
                message(ChatSender.USER, message),
                contextMapper.toJson(previous)
        ));
        when(understandingService.analyze(message, previous)).thenReturn(understanding);
        when(contextResolver.resolve(previous, understanding)).thenReturn(resolution);
        when(groundingService.resolve(message, resolution)).thenReturn(grounding);
        when(promptBuilder.build(message, grounding)).thenReturn(new ChatPrompt("system", "user"));
        when(gateway.generate("system", "user")).thenThrow(new IllegalStateException(
                "provider failure",
                new InterruptedIOException("timeout")
        ));

        assertThatThrownBy(() -> orchestrator(Optional.of(gateway)).sendMessage(
                userId,
                new SendChatMessageRequest(message)
        )).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE);
            assertThat(exception.getMessage())
                    .isEqualTo("Trợ lý AI đang phản hồi chậm. Vui lòng thử lại sau.");
        });

        verify(persistenceService, never()).completeTurn(any(), any(), any(), any());
    }

    private ChatOrchestrator orchestrator(Optional<AiChatGateway> optionalGateway) {
        return new ChatOrchestrator(
                persistenceService,
                contextMapper,
                understandingService,
                contextResolver,
                groundingService,
                promptBuilder,
                new ChatResponseValidator(),
                optionalGateway,
                objectMapper
        );
    }

    private ChatMessageResponse message(ChatSender sender, String content) {
        return new ChatMessageResponse(
                UUID.randomUUID(),
                sender,
                content,
                JsonNodeFactory.instance.objectNode(),
                NOW
        );
    }

    private DentalServiceResponse dentalService(String name) {
        return new DentalServiceResponse(
                UUID.randomUUID(),
                name,
                "Dịch vụ nha khoa",
                new BigDecimal("200000"),
                null,
                true,
                NOW,
                NOW
        );
    }
}
