package com.dentalclinic.chat.service;

import com.dentalclinic.ai.conversation.mapper.ChatSemanticContextMapper;
import com.dentalclinic.ai.conversation.model.ChatContextResolution;
import com.dentalclinic.ai.conversation.model.ChatIntent;
import com.dentalclinic.ai.conversation.model.ChatSemanticContext;
import com.dentalclinic.ai.conversation.model.ChatUnderstanding;
import com.dentalclinic.ai.conversation.service.ChatContextResolver;
import com.dentalclinic.ai.conversation.service.ConversationUnderstandingService;
import com.dentalclinic.ai.gateway.AiChatGateway;
import com.dentalclinic.ai.gateway.AiCallExecutor;
import com.dentalclinic.ai.gateway.AiCallType;
import com.dentalclinic.ai.gateway.AiProviderFailureClassifier;
import com.dentalclinic.ai.knowledge.model.KnowledgeSourceTrace;
import com.dentalclinic.ai.orchestration.model.ChatGroundingResult;
import com.dentalclinic.ai.orchestration.model.ChatPrompt;
import com.dentalclinic.ai.orchestration.service.ChatGroundingService;
import com.dentalclinic.ai.orchestration.service.ChatPromptBuilder;
import com.dentalclinic.ai.orchestration.service.ChatResponseValidator;
import com.dentalclinic.ai.orchestration.service.DatabaseChatResponseService;
import com.dentalclinic.ai.telemetry.ChatTelemetry;
import com.dentalclinic.chat.dto.ChatMessageResponse;
import com.dentalclinic.chat.dto.ChatSourceResponse;
import com.dentalclinic.chat.dto.SendChatMessageRequest;
import com.dentalclinic.chat.dto.SendChatMessageResponse;
import com.dentalclinic.chat.model.PreparedChatTurn;
import com.dentalclinic.chat.model.ChatTurnStart;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InterruptedIOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

@Service
public class ChatOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrator.class);
    private static final int MAX_FALLBACK_KNOWLEDGE_CHARS = 1_500;
    private static final String OUT_OF_SCOPE_ANSWER =
            "Tôi chỉ hỗ trợ kiến thức nha khoa và việc tìm dịch vụ, bác sĩ hoặc lịch khám phù hợp.";
    private static final String INSUFFICIENT_KNOWLEDGE_ANSWER =
            "Kho kiến thức hiện chưa có đủ dữ liệu đáng tin cậy để trả lời nội dung này. "
                    + "Bạn nên đến phòng khám để được bác sĩ đánh giá trực tiếp.";
    private final ChatPersistenceService persistenceService;
    private final ChatSemanticContextMapper contextMapper;
    private final ConversationUnderstandingService understandingService;
    private final ChatContextResolver contextResolver;
    private final ChatGroundingService groundingService;
    private final DatabaseChatResponseService databaseChatResponseService;
    private final ChatPromptBuilder promptBuilder;
    private final ChatResponseValidator responseValidator;
    private final Optional<AiChatGateway> aiChatGateway;
    private final AiCallExecutor aiCallExecutor;
    private final ObjectMapper objectMapper;

    public ChatOrchestrator(
            ChatPersistenceService persistenceService,
            ChatSemanticContextMapper contextMapper,
            ConversationUnderstandingService understandingService,
            ChatContextResolver contextResolver,
            ChatGroundingService groundingService,
            DatabaseChatResponseService databaseChatResponseService,
            ChatPromptBuilder promptBuilder,
            ChatResponseValidator responseValidator,
            Optional<AiChatGateway> aiChatGateway,
            AiCallExecutor aiCallExecutor,
            ObjectMapper objectMapper
    ) {
        this.persistenceService = persistenceService;
        this.contextMapper = contextMapper;
        this.understandingService = understandingService;
        this.contextResolver = contextResolver;
        this.groundingService = groundingService;
        this.databaseChatResponseService = databaseChatResponseService;
        this.promptBuilder = promptBuilder;
        this.responseValidator = responseValidator;
        this.aiChatGateway = aiChatGateway;
        this.aiCallExecutor = aiCallExecutor;
        this.objectMapper = objectMapper;
    }

    public SendChatMessageResponse sendMessage(UUID userId, SendChatMessageRequest request) {
        PreparedChatTurn prepared = prepareTurn(userId, request);
        boolean fallbackUsed = false;
        try {
            String answer;
            if (prepared.requiresGeneration()) {
                try {
                    AiChatGateway gateway = aiChatGateway.orElseThrow(() -> new BusinessException(
                            ErrorCode.AI_SERVICE_UNAVAILABLE,
                            AiProviderFailureClassifier.SYSTEM_ERROR_MESSAGE
                    ));
                    answer = generate(prepared, gateway);
                } catch (RuntimeException generationFailure) {
                    if (prepared.generationFailureFallback() == null) {
                        throw generationFailure;
                    }
                    fallbackUsed = true;
                    log.warn(
                            "chat_generation_fallback requestId={} providerStatus={} generationMode=NON_STREAM",
                            prepared.requestId(),
                            AiProviderFailureClassifier.classify(generationFailure)
                    );
                    answer = prepared.generationFailureFallback();
                }
            } else {
                answer = prepared.immediateAnswer();
            }
            SendChatMessageResponse response = completeTurn(prepared, answer);
            log.info(
                    "chat_request_completed requestId={} totalMs={} intents={} generationRequired={} fallbackUsed={} transport=HTTP",
                    prepared.requestId(),
                    ChatTelemetry.elapsedMillis(prepared.requestStartedNanos()),
                    response.intents(),
                    prepared.requiresGeneration(),
                    fallbackUsed
            );
            return response;
        } catch (RuntimeException exception) {
            log.warn(
                    "chat_request_failed requestId={} totalMs={} stage=RESPONSE providerStatus={} transport=HTTP",
                    prepared.requestId(),
                    ChatTelemetry.elapsedMillis(prepared.requestStartedNanos()),
                    AiProviderFailureClassifier.classify(exception)
            );
            try {
                discardTurn(prepared);
            } catch (RuntimeException ignored) {
                // Preserve the original AI failure returned to the caller.
            }
            throw exception;
        }
    }

    public PreparedChatTurn prepareTurn(UUID userId, SendChatMessageRequest request) {
        String requestId = ChatTelemetry.newRequestId();
        long requestStartedNanos = System.nanoTime();
        String stage = "CONTEXT";
        try (var ignored = ChatTelemetry.open(requestId)) {
            String message = request.message().trim();
            long contextStartedNanos = System.nanoTime();
            ChatSemanticContext previousContext = readContext(
                    persistenceService.readContext(userId)
            );
            long contextMs = ChatTelemetry.elapsedMillis(contextStartedNanos);

            stage = "CLASSIFICATION";
            long classificationStartedNanos = System.nanoTime();
            ChatUnderstanding understanding = understandingService.analyze(message, previousContext);
            long classificationMs = ChatTelemetry.elapsedMillis(classificationStartedNanos);

            stage = "CONTEXT_RESOLUTION";
            long resolutionStartedNanos = System.nanoTime();
            ChatContextResolution resolution = contextResolver.resolve(
                    previousContext,
                    understanding
            );
            long resolutionMs = ChatTelemetry.elapsedMillis(resolutionStartedNanos);

            stage = "GROUNDING";
            long groundingStartedNanos = System.nanoTime();
            ChatGroundingResult grounding = resolution.needsClarification()
                    ? ChatGroundingResult.empty(resolution.context())
                    : groundingService.resolve(message, resolution);
            long groundingMs = ChatTelemetry.elapsedMillis(groundingStartedNanos);

            stage = "PROMPT";
            long promptStartedNanos = System.nanoTime();
            String immediateAnswer = resolveImmediateAnswer(resolution, grounding);
            ChatPrompt prompt = immediateAnswer == null
                    ? promptBuilder.build(message, grounding)
                    : null;
            String generationFailureFallback = prompt == null
                    ? null
                    : resolveGenerationFailureFallback(grounding);
            long promptMs = ChatTelemetry.elapsedMillis(promptStartedNanos);
            if (Thread.currentThread().isInterrupted()) {
                throw new BusinessException(
                        ErrorCode.AI_SERVICE_UNAVAILABLE,
                        AiProviderFailureClassifier.SYSTEM_ERROR_MESSAGE
                );
            }

            stage = "PERSIST_USER_MESSAGE";
            long persistenceStartedNanos = System.nanoTime();
            ChatTurnStart turn = persistenceService.startTurn(userId, message);
            long persistenceMs = ChatTelemetry.elapsedMillis(persistenceStartedNanos);
            PreparedChatTurn prepared = new PreparedChatTurn(
                    userId,
                    turn,
                    resolution,
                    grounding,
                    prompt,
                    immediateAnswer,
                    generationFailureFallback,
                    requestId,
                    requestStartedNanos
            );
            log.info(
                    "chat_preparation_completed requestId={} totalMs={} contextMs={} classificationMs={} resolutionMs={} groundingMs={} promptMs={} persistenceMs={} intents={} needsClarification={} generationRequired={}",
                    requestId,
                    ChatTelemetry.elapsedMillis(requestStartedNanos),
                    contextMs,
                    classificationMs,
                    resolutionMs,
                    groundingMs,
                    promptMs,
                    persistenceMs,
                    grounding.context().lastIntents(),
                    resolution.needsClarification(),
                    prepared.requiresGeneration()
            );
            return prepared;
        } catch (RuntimeException exception) {
            log.warn(
                    "chat_preparation_failed requestId={} totalMs={} stage={} providerStatus={}",
                    requestId,
                    ChatTelemetry.elapsedMillis(requestStartedNanos),
                    stage,
                    AiProviderFailureClassifier.classify(exception)
            );
            throw exception;
        }
    }

    public void discardTurn(PreparedChatTurn prepared) {
        persistenceService.discardUserMessage(
                prepared.userId(),
                prepared.turn().userMessage().id()
        );
    }

    public SendChatMessageResponse completeTurn(PreparedChatTurn prepared, String rawAnswer) {
        String answer = responseValidator.validate(rawAnswer);
        ChatGroundingResult grounding = prepared.grounding();
        List<ChatSourceResponse> sources = mapSources(grounding);
        ObjectNode metadata = metadata(
                grounding.context().lastIntents(),
                sources,
                grounding,
                prepared.resolution().needsClarification()
        );
        ChatMessageResponse assistantMessage = persistenceService.completeTurn(
                prepared.userId(),
                answer,
                metadata,
                contextMapper.toJson(grounding.context())
        );
        return new SendChatMessageResponse(
                prepared.turn().userMessage().id(),
                assistantMessage.id(),
                assistantMessage.content(),
                grounding.context().lastIntents(),
                sources,
                grounding.services(),
                grounding.doctors(),
                grounding.slots(),
                prepared.resolution().needsClarification(),
                assistantMessage.createdAt()
        );
    }

    private ChatSemanticContext readContext(JsonNode contextData) {
        try {
            return contextMapper.fromJson(contextData);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.AI_SERVICE_UNAVAILABLE,
                    AiProviderFailureClassifier.SYSTEM_ERROR_MESSAGE
            );
        }
    }

    private String resolveImmediateAnswer(
            ChatContextResolution resolution,
            ChatGroundingResult grounding
    ) {
        if (resolution.needsClarification()) {
            return resolution.clarificationQuestion();
        }
        if (grounding.context().lastIntents().equals(List.of(ChatIntent.OUT_OF_SCOPE))) {
            return OUT_OF_SCOPE_ANSWER;
        }
        Optional<String> databaseAnswer = databaseChatResponseService.respond(resolution, grounding);
        if (databaseAnswer.isPresent()) {
            log.info(
                    "chat_immediate_answer_selected requestId={} mode=DATABASE_TEMPLATE intents={}",
                    ChatTelemetry.currentRequestId(),
                    grounding.context().lastIntents()
            );
            return databaseAnswer.orElseThrow();
        }
        if (grounding.knowledgeRequired()
                && (!grounding.knowledgeAvailable() || grounding.knowledge().isEmpty())) {
            String verifiedPrice = verifiedServicePriceAnswer(grounding);
            return verifiedPrice == null
                    ? INSUFFICIENT_KNOWLEDGE_ANSWER
                    : verifiedPrice + " " + INSUFFICIENT_KNOWLEDGE_ANSWER;
        }
        return null;
    }

    private String resolveGenerationFailureFallback(ChatGroundingResult grounding) {
        if (!grounding.context().lastIntents().contains(ChatIntent.SYMPTOM_CONSULTATION)) {
            return null;
        }
        String verifiedPrice = verifiedServicePriceAnswer(grounding);
        String verifiedKnowledge = verifiedKnowledgeFallback(grounding);
        if (verifiedPrice != null && verifiedKnowledge != null) {
            return verifiedPrice + "\n\n" + verifiedKnowledge;
        }
        if (verifiedKnowledge != null) {
            return verifiedKnowledge;
        }
        return verifiedPrice == null
                ? null
                : verifiedPrice + " Phần tư vấn triệu chứng: "
                        + AiProviderFailureClassifier.SYSTEM_ERROR_MESSAGE + ".";
    }

    private String verifiedKnowledgeFallback(ChatGroundingResult grounding) {
        return grounding.knowledge().stream()
                .map(result -> result.content() == null ? "" : result.content().strip())
                .filter(content -> !content.isBlank())
                .findFirst()
                .map(content -> {
                    String safeContent = content.length() <= MAX_FALLBACK_KNOWLEDGE_CHARS
                            ? content
                            : content.substring(0, MAX_FALLBACK_KNOWLEDGE_CHARS - 3).stripTrailing()
                                    + "...";
                    return "Thông tin tham khảo từ kho kiến thức nha khoa:\n"
                            + safeContent
                            + "\nBạn nên đến phòng khám để được bác sĩ kiểm tra trực tiếp.";
                })
                .orElse(null);
    }

    private String verifiedServicePriceAnswer(ChatGroundingResult grounding) {
        if (!grounding.context().lastIntents().contains(ChatIntent.SERVICE_CATALOG)
                || grounding.context().selectedService() == null
                || grounding.services().size() != 1) {
            return null;
        }
        var service = grounding.services().getFirst();
        String formattedPrice = java.text.NumberFormat
                .getNumberInstance(java.util.Locale.forLanguageTag("vi-VN"))
                .format(service.price());
        return "Dịch vụ %s hiện có giá %s đ."
                .formatted(service.name(), formattedPrice);
    }

    private String generate(PreparedChatTurn prepared, AiChatGateway gateway) {
        long generationStartedNanos = System.nanoTime();
        ChatPrompt prompt = prepared.prompt();
        try {
            String answer = aiCallExecutor.execute(
                    AiCallType.GENERATION,
                    () -> gateway.generate(prompt.systemPrompt(), prompt.userPrompt())
            );
            log.info(
                    "chat_generation_completed requestId={} durationMs={} generationMode=NON_STREAM",
                    prepared.requestId(),
                    ChatTelemetry.elapsedMillis(generationStartedNanos)
            );
            return answer;
        } catch (BusinessException exception) {
            log.warn(
                    "chat_generation_failed requestId={} durationMs={} providerStatus={} generationMode=NON_STREAM",
                    prepared.requestId(),
                    ChatTelemetry.elapsedMillis(generationStartedNanos),
                    AiProviderFailureClassifier.classify(exception)
            );
            throw exception;
        } catch (RuntimeException exception) {
            log.warn(
                    "chat_generation_failed requestId={} durationMs={} providerStatus={} generationMode=NON_STREAM",
                    prepared.requestId(),
                    ChatTelemetry.elapsedMillis(generationStartedNanos),
                    AiProviderFailureClassifier.classify(exception)
            );
            if (AiProviderFailureClassifier.isQuotaExceeded(exception)) {
                throw new BusinessException(
                        ErrorCode.AI_SERVICE_UNAVAILABLE,
                        AiProviderFailureClassifier.SYSTEM_ERROR_MESSAGE
                );
            }
            if (isTimeout(exception)) {
                throw new BusinessException(
                        ErrorCode.AI_SERVICE_UNAVAILABLE,
                        AiProviderFailureClassifier.SYSTEM_ERROR_MESSAGE
                );
            }
            throw new BusinessException(
                    ErrorCode.AI_SERVICE_UNAVAILABLE,
                    AiProviderFailureClassifier.SYSTEM_ERROR_MESSAGE
            );
        }
    }

    private boolean isTimeout(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof TimeoutException || current instanceof InterruptedIOException) {
                return true;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                return false;
            }
            current = cause;
        }
        return false;
    }

    private List<ChatSourceResponse> mapSources(ChatGroundingResult grounding) {
        LinkedHashSet<ChatSourceResponse> sources = new LinkedHashSet<>();
        grounding.knowledge().stream()
                .map(result -> result.source())
                .filter(java.util.Objects::nonNull)
                .map(this::toSourceResponse)
                .forEach(sources::add);
        return List.copyOf(sources);
    }

    private ChatSourceResponse toSourceResponse(KnowledgeSourceTrace source) {
        return new ChatSourceResponse(
                source.recordId(),
                source.title(),
                source.sourceUrl()
        );
    }

    private ObjectNode metadata(
            List<ChatIntent> intents,
            List<ChatSourceResponse> sources,
            ChatGroundingResult grounding,
            boolean needsClarification
    ) {
        Map<String, Object> values = Map.of(
                "intents", intents,
                "sources", sources,
                "suggestedServices", grounding.services(),
                "suggestedDoctors", grounding.doctors(),
                "suggestedSlots", grounding.slots(),
                "needsClarification", needsClarification
        );
        return objectMapper.valueToTree(values);
    }
}
