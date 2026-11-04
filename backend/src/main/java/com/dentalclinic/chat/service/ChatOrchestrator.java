package com.dentalclinic.chat.service;

import com.dentalclinic.ai.conversation.mapper.ChatSemanticContextMapper;
import com.dentalclinic.ai.conversation.model.ChatContextResolution;
import com.dentalclinic.ai.conversation.model.ChatIntent;
import com.dentalclinic.ai.conversation.model.ChatSemanticContext;
import com.dentalclinic.ai.conversation.model.ChatUnderstanding;
import com.dentalclinic.ai.conversation.service.ChatContextResolver;
import com.dentalclinic.ai.conversation.service.ConversationUnderstandingService;
import com.dentalclinic.ai.gateway.AiChatGateway;
import com.dentalclinic.ai.gateway.AiProviderFailureClassifier;
import com.dentalclinic.ai.knowledge.model.KnowledgeSourceTrace;
import com.dentalclinic.ai.orchestration.model.ChatGroundingResult;
import com.dentalclinic.ai.orchestration.model.ChatPrompt;
import com.dentalclinic.ai.orchestration.service.ChatGroundingService;
import com.dentalclinic.ai.orchestration.service.ChatPromptBuilder;
import com.dentalclinic.ai.orchestration.service.ChatResponseValidator;
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
import org.springframework.stereotype.Service;

import java.io.InterruptedIOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
public class ChatOrchestrator {

    private static final String OUT_OF_SCOPE_ANSWER =
            "Tôi chỉ hỗ trợ kiến thức nha khoa và việc tìm dịch vụ, bác sĩ hoặc lịch khám phù hợp.";
    private static final String INSUFFICIENT_KNOWLEDGE_ANSWER =
            "Kho kiến thức hiện chưa có đủ dữ liệu đáng tin cậy để trả lời nội dung này. "
                    + "Bạn nên đến phòng khám để được bác sĩ đánh giá trực tiếp.";
    private static final String AI_TIMEOUT_MESSAGE =
            "Trợ lý AI đang phản hồi chậm. Vui lòng thử lại sau.";

    private final ChatPersistenceService persistenceService;
    private final ChatSemanticContextMapper contextMapper;
    private final ConversationUnderstandingService understandingService;
    private final ChatContextResolver contextResolver;
    private final ChatGroundingService groundingService;
    private final ChatPromptBuilder promptBuilder;
    private final ChatResponseValidator responseValidator;
    private final Optional<AiChatGateway> aiChatGateway;
    private final ObjectMapper objectMapper;

    public ChatOrchestrator(
            ChatPersistenceService persistenceService,
            ChatSemanticContextMapper contextMapper,
            ConversationUnderstandingService understandingService,
            ChatContextResolver contextResolver,
            ChatGroundingService groundingService,
            ChatPromptBuilder promptBuilder,
            ChatResponseValidator responseValidator,
            Optional<AiChatGateway> aiChatGateway,
            ObjectMapper objectMapper
    ) {
        this.persistenceService = persistenceService;
        this.contextMapper = contextMapper;
        this.understandingService = understandingService;
        this.contextResolver = contextResolver;
        this.groundingService = groundingService;
        this.promptBuilder = promptBuilder;
        this.responseValidator = responseValidator;
        this.aiChatGateway = aiChatGateway;
        this.objectMapper = objectMapper;
    }

    public SendChatMessageResponse sendMessage(UUID userId, SendChatMessageRequest request) {
        AiChatGateway gateway = aiChatGateway.orElseThrow(() -> new BusinessException(
                ErrorCode.AI_SERVICE_UNAVAILABLE,
                "Trợ lý AI chưa được bật trên hệ thống"
        ));
        PreparedChatTurn prepared = prepareTurn(userId, request);
        String answer = prepared.requiresGeneration()
                ? generate(prepared.prompt(), gateway)
                : prepared.immediateAnswer();
        return completeTurn(prepared, answer);
    }

    public PreparedChatTurn prepareTurn(UUID userId, SendChatMessageRequest request) {
        String message = request.message().trim();
        ChatTurnStart turn = persistenceService.startTurn(userId, message);
        ChatSemanticContext previousContext = readContext(turn.contextData());
        ChatUnderstanding understanding = understandingService.analyze(message, previousContext);
        ChatContextResolution resolution = contextResolver.resolve(previousContext, understanding);
        ChatGroundingResult grounding = resolution.needsClarification()
                ? ChatGroundingResult.empty(resolution.context())
                : groundingService.resolve(message, resolution);

        String immediateAnswer = resolveImmediateAnswer(resolution, grounding);
        ChatPrompt prompt = immediateAnswer == null
                ? promptBuilder.build(message, grounding)
                : null;
        return new PreparedChatTurn(
                userId,
                turn,
                resolution,
                grounding,
                prompt,
                immediateAnswer
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
                    "Ngữ cảnh hội thoại hiện tại không hợp lệ"
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
        if (grounding.context().lastIntents().contains(ChatIntent.SERVICE_CATALOG)) {
            return serviceCatalogAnswer(grounding);
        }
        if (grounding.knowledgeRequired()
                && (!grounding.knowledgeAvailable() || grounding.knowledge().isEmpty())) {
            return INSUFFICIENT_KNOWLEDGE_ANSWER;
        }
        return null;
    }

    private String serviceCatalogAnswer(ChatGroundingResult grounding) {
        if (grounding.services().isEmpty()) {
            return "Hiện tại phòng khám chưa có dịch vụ nha khoa đang hoạt động.";
        }
        String serviceNames = grounding.services().stream()
                .map(service -> "- " + service.name())
                .collect(Collectors.joining("\n"));
        return "Hiện tại Nha Khoa An Tâm có các dịch vụ đang hoạt động:\n" + serviceNames
                + "\nBạn có thể xem thông tin và mức giá ở các thẻ dịch vụ bên dưới.";
    }

    private String generate(ChatPrompt prompt, AiChatGateway gateway) {
        try {
            return gateway.generate(prompt.systemPrompt(), prompt.userPrompt());
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (AiProviderFailureClassifier.isQuotaExceeded(exception)) {
                throw new BusinessException(
                        ErrorCode.AI_SERVICE_UNAVAILABLE,
                        AiProviderFailureClassifier.QUOTA_EXCEEDED_MESSAGE
                );
            }
            if (isTimeout(exception)) {
                throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, AI_TIMEOUT_MESSAGE);
            }
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
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
