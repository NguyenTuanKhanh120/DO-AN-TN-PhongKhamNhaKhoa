package com.dentalclinic.chat.service;

import com.dentalclinic.ai.gateway.AiStreamingChatGateway;
import com.dentalclinic.ai.gateway.AiProviderFailureClassifier;
import com.dentalclinic.ai.orchestration.service.ChatResponseValidator;
import com.dentalclinic.chat.dto.ChatStreamCompletedEvent;
import com.dentalclinic.chat.dto.ChatStreamErrorEvent;
import com.dentalclinic.chat.dto.ChatStreamMetadataEvent;
import com.dentalclinic.chat.dto.ChatStreamStartedEvent;
import com.dentalclinic.chat.dto.ChatStreamTokenEvent;
import com.dentalclinic.chat.dto.SendChatMessageRequest;
import com.dentalclinic.chat.dto.SendChatMessageResponse;
import com.dentalclinic.chat.model.PreparedChatTurn;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ChatStreamingService {

    private static final String EVENT_STARTED = "started";
    private static final String EVENT_TOKEN = "token";
    private static final String EVENT_METADATA = "metadata";
    private static final String EVENT_COMPLETED = "completed";
    private static final String EVENT_ERROR = "error";

    private final ChatOrchestrator chatOrchestrator;
    private final ChatResponseValidator responseValidator;
    private final Optional<AiStreamingChatGateway> streamingGateway;
    private final Duration streamTimeout;

    public ChatStreamingService(
            ChatOrchestrator chatOrchestrator,
            ChatResponseValidator responseValidator,
            Optional<AiStreamingChatGateway> streamingGateway,
            @Value("${app.ai.streaming-timeout:75s}") Duration streamTimeout
    ) {
        this.chatOrchestrator = chatOrchestrator;
        this.responseValidator = responseValidator;
        this.streamingGateway = streamingGateway;
        if (streamTimeout == null || streamTimeout.isZero() || streamTimeout.isNegative()) {
            throw new IllegalArgumentException("AI streaming timeout must be greater than zero");
        }
        this.streamTimeout = streamTimeout;
    }

    public Flux<ServerSentEvent<Object>> streamMessage(
            UUID userId,
            SendChatMessageRequest request
    ) {
        return Flux.defer(() -> {
            AiStreamingChatGateway gateway = streamingGateway.orElseThrow(() ->
                    new BusinessException(
                            ErrorCode.AI_SERVICE_UNAVAILABLE,
                            "Trợ lý AI chưa được bật trên hệ thống"
                    )
            );
            return Mono.fromCallable(() -> chatOrchestrator.prepareTurn(userId, request))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMapMany(prepared -> streamPreparedTurn(prepared, gateway));
        }).onErrorResume(error -> Flux.just(errorEvent(error, false)));
    }

    private Flux<ServerSentEvent<Object>> streamPreparedTurn(
            PreparedChatTurn prepared,
            AiStreamingChatGateway gateway
    ) {
        ServerSentEvent<Object> started = event(
                EVENT_STARTED,
                new ChatStreamStartedEvent(
                        prepared.turn().userMessage().id(),
                        prepared.turn().userMessage().createdAt()
                )
        );
        Flux<ServerSentEvent<Object>> answerEvents = prepared.requiresGeneration()
                ? streamGeneratedAnswer(prepared, gateway)
                : streamImmediateAnswer(prepared);
        return Flux.concat(Flux.just(started), answerEvents);
    }

    private Flux<ServerSentEvent<Object>> streamGeneratedAnswer(
            PreparedChatTurn prepared,
            AiStreamingChatGateway gateway
    ) {
        StringBuilder answer = new StringBuilder();
        AtomicBoolean tokenEmitted = new AtomicBoolean(false);
        Flux<ServerSentEvent<Object>> tokens = Flux.defer(() -> gateway.stream(
                prepared.prompt().systemPrompt(),
                prepared.prompt().userPrompt()
        ))
                .map(token -> {
                    answer.append(token);
                    responseValidator.validatePartial(answer.toString());
                    tokenEmitted.set(true);
                    return event(EVENT_TOKEN, new ChatStreamTokenEvent(token));
                })
                .timeout(streamTimeout);

        return tokens
                .concatWith(complete(prepared, answer::toString))
                .onErrorResume(error -> Flux.just(errorEvent(error, tokenEmitted.get())));
    }

    private Flux<ServerSentEvent<Object>> streamImmediateAnswer(PreparedChatTurn prepared) {
        AtomicBoolean tokenEmitted = new AtomicBoolean(false);
        return Flux.defer(() -> {
                    responseValidator.validatePartial(prepared.immediateAnswer());
                    tokenEmitted.set(true);
                    return Flux.concat(
                            Flux.just(event(
                                    EVENT_TOKEN,
                                    new ChatStreamTokenEvent(prepared.immediateAnswer())
                            )),
                            complete(prepared, prepared::immediateAnswer)
                    );
                })
                .onErrorResume(error -> Flux.just(errorEvent(error, tokenEmitted.get())));
    }

    private Flux<ServerSentEvent<Object>> complete(
            PreparedChatTurn prepared,
            java.util.function.Supplier<String> answerSupplier
    ) {
        return Mono.fromCallable(() -> chatOrchestrator.completeTurn(
                        prepared,
                        answerSupplier.get()
                ))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(this::completionEvents);
    }

    private Flux<ServerSentEvent<Object>> completionEvents(SendChatMessageResponse response) {
        ChatStreamMetadataEvent metadata = new ChatStreamMetadataEvent(
                response.intents(),
                response.sources(),
                response.suggestedServices(),
                response.suggestedDoctors(),
                response.suggestedSlots(),
                response.needsClarification()
        );
        ChatStreamCompletedEvent completed = new ChatStreamCompletedEvent(
                response.messageId(),
                response.createdAt()
        );
        return Flux.just(
                event(EVENT_METADATA, metadata),
                event(EVENT_COMPLETED, completed)
        );
    }

    private ServerSentEvent<Object> errorEvent(Throwable error, boolean partial) {
        ErrorCode errorCode;
        String message;
        if (error instanceof BusinessException businessException) {
            errorCode = businessException.getErrorCode();
            message = businessException.getMessage();
        } else if (AiProviderFailureClassifier.isQuotaExceeded(error)) {
            errorCode = ErrorCode.AI_SERVICE_UNAVAILABLE;
            message = AiProviderFailureClassifier.QUOTA_EXCEEDED_MESSAGE;
        } else if (error instanceof TimeoutException) {
            errorCode = ErrorCode.AI_SERVICE_UNAVAILABLE;
            message = "Phản hồi AI đã quá thời gian chờ";
        } else {
            errorCode = ErrorCode.AI_SERVICE_UNAVAILABLE;
            message = errorCode.getDefaultMessage();
        }
        return event(
                EVENT_ERROR,
                new ChatStreamErrorEvent(errorCode.name(), message, partial)
        );
    }

    private ServerSentEvent<Object> event(String name, Object data) {
        return ServerSentEvent.builder(data).event(name).build();
    }
}
