package com.dentalclinic.chat.service;

import com.dentalclinic.ai.gateway.AiStreamingChatGateway;
import com.dentalclinic.ai.gateway.AiCallExecutor;
import com.dentalclinic.ai.gateway.AiCallType;
import com.dentalclinic.ai.gateway.AiProviderFailureClassifier;
import com.dentalclinic.ai.orchestration.service.ChatResponseValidator;
import com.dentalclinic.ai.telemetry.ChatTelemetry;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ChatStreamingService {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamingService.class);
    private static final String EVENT_STARTED = "started";
    private static final String EVENT_TOKEN = "token";
    private static final String EVENT_METADATA = "metadata";
    private static final String EVENT_COMPLETED = "completed";
    private static final String EVENT_ERROR = "error";

    private final ChatOrchestrator chatOrchestrator;
    private final ChatResponseValidator responseValidator;
    private final Optional<AiStreamingChatGateway> streamingGateway;
    private final AiCallExecutor aiCallExecutor;
    private final Duration understandingTimeout;
    private final Duration streamTimeout;

    public ChatStreamingService(
            ChatOrchestrator chatOrchestrator,
            ChatResponseValidator responseValidator,
            Optional<AiStreamingChatGateway> streamingGateway,
            AiCallExecutor aiCallExecutor,
            @Value("${app.ai.understanding-timeout:6s}") Duration understandingTimeout,
            @Value("${app.ai.streaming-timeout:5s}") Duration streamTimeout
    ) {
        this.chatOrchestrator = chatOrchestrator;
        this.responseValidator = responseValidator;
        this.streamingGateway = streamingGateway;
        this.aiCallExecutor = aiCallExecutor;
        if (understandingTimeout == null
                || understandingTimeout.isZero()
                || understandingTimeout.isNegative()) {
            throw new IllegalArgumentException("AI understanding timeout must be greater than zero");
        }
        this.understandingTimeout = understandingTimeout;
        if (streamTimeout == null || streamTimeout.isZero() || streamTimeout.isNegative()) {
            throw new IllegalArgumentException("AI streaming timeout must be greater than zero");
        }
        this.streamTimeout = streamTimeout;
    }

    public Flux<ServerSentEvent<Object>> streamMessage(
            UUID userId,
            SendChatMessageRequest request
    ) {
        return Flux.defer(() -> Mono.fromCallable(() ->
                        chatOrchestrator.prepareTurn(userId, request)
                )
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(understandingTimeout)
                .flatMapMany(this::streamPreparedTurn)
        ).onErrorResume(error -> Flux.just(errorEvent(error, false)));
    }

    private Flux<ServerSentEvent<Object>> streamPreparedTurn(PreparedChatTurn prepared) {
        ServerSentEvent<Object> started = event(
                EVENT_STARTED,
                new ChatStreamStartedEvent(
                        prepared.turn().userMessage().id(),
                        prepared.turn().userMessage().createdAt()
                )
        );
        Flux<ServerSentEvent<Object>> answerEvents;
        if (prepared.requiresGeneration()) {
            answerEvents = streamingGateway
                    .<Flux<ServerSentEvent<Object>>>map(gateway ->
                            streamGeneratedAnswer(prepared, gateway)
                    )
                    .orElseGet(() -> discardAndError(
                            prepared,
                            new BusinessException(
                                    ErrorCode.AI_SERVICE_UNAVAILABLE,
                                    AiProviderFailureClassifier.SYSTEM_ERROR_MESSAGE
                            ),
                            false
                    ));
        } else {
            answerEvents = streamImmediateAnswer(prepared);
        }
        return Flux.concat(Flux.just(started), answerEvents);
    }

    private Flux<ServerSentEvent<Object>> streamGeneratedAnswer(
            PreparedChatTurn prepared,
            AiStreamingChatGateway gateway
    ) {
        long generationStartedNanos = System.nanoTime();
        StringBuilder answer = new StringBuilder();
        AtomicBoolean tokenEmitted = new AtomicBoolean(false);
        AtomicLong firstTokenMs = new AtomicLong(-1L);
        Flux<ServerSentEvent<Object>> tokens = Flux.defer(() ->
                        Mono.fromCallable(() -> {
                                    aiCallExecutor.ensureAvailable(AiCallType.STREAMING);
                                    return gateway.stream(
                                            prepared.prompt().systemPrompt(),
                                            prepared.prompt().userPrompt()
                                    );
                                })
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMapMany(providerTokens -> providerTokens)
                )
                .map(token -> {
                    answer.append(token);
                    responseValidator.validatePartial(answer.toString());
                    tokenEmitted.set(true);
                    if (firstTokenMs.compareAndSet(
                            -1L,
                            ChatTelemetry.elapsedMillis(generationStartedNanos)
                    )) {
                        log.info(
                                "chat_generation_first_token requestId={} firstTokenMs={} generationMode=STREAM",
                                prepared.requestId(),
                                firstTokenMs.get()
                        );
                    }
                    return event(EVENT_TOKEN, new ChatStreamTokenEvent(token));
                })
                .timeout(streamTimeout)
                .doOnError(error -> aiCallExecutor.recordFailure(
                        AiCallType.STREAMING,
                        error
                ))
                .doOnCancel(() -> aiCallExecutor.recordCancellation(AiCallType.STREAMING))
                .doOnComplete(() -> aiCallExecutor.recordSuccess(AiCallType.STREAMING))
                .doOnComplete(() -> log.info(
                        "chat_generation_completed requestId={} durationMs={} firstTokenMs={} generationMode=STREAM",
                        prepared.requestId(),
                        ChatTelemetry.elapsedMillis(generationStartedNanos),
                        firstTokenMs.get()
                ));

        return tokens
                .concatWith(complete(prepared, answer::toString, false))
                .onErrorResume(error -> {
                    if (!tokenEmitted.get()
                            && prepared.generationFailureFallback() != null) {
                        log.warn(
                                "chat_generation_fallback requestId={} durationMs={} providerStatus={} generationMode=STREAM",
                                prepared.requestId(),
                                ChatTelemetry.elapsedMillis(generationStartedNanos),
                                AiProviderFailureClassifier.classify(error)
                        );
                        return streamAnswer(
                                prepared,
                                prepared.generationFailureFallback(),
                                true
                        );
                    }
                    return discardAndError(prepared, error, tokenEmitted.get());
                });
    }

    private Flux<ServerSentEvent<Object>> streamImmediateAnswer(PreparedChatTurn prepared) {
        return streamAnswer(prepared, prepared.immediateAnswer(), false);
    }

    private Flux<ServerSentEvent<Object>> streamAnswer(
            PreparedChatTurn prepared,
            String answer,
            boolean fallbackUsed
    ) {
        AtomicBoolean tokenEmitted = new AtomicBoolean(false);
        return Flux.defer(() -> {
                    responseValidator.validatePartial(answer);
                    tokenEmitted.set(true);
                    return Flux.concat(
                            Flux.just(event(
                                    EVENT_TOKEN,
                                    new ChatStreamTokenEvent(answer)
                            )),
                            complete(prepared, () -> answer, fallbackUsed)
                    );
                })
                .onErrorResume(error -> discardAndError(prepared, error, tokenEmitted.get()));
    }

    private Flux<ServerSentEvent<Object>> complete(
            PreparedChatTurn prepared,
            java.util.function.Supplier<String> answerSupplier,
            boolean fallbackUsed
    ) {
        return Mono.fromCallable(() -> chatOrchestrator.completeTurn(
                        prepared,
                        answerSupplier.get()
                ))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(response -> log.info(
                        "chat_request_completed requestId={} totalMs={} intents={} generationRequired={} fallbackUsed={} transport=SSE",
                        prepared.requestId(),
                        ChatTelemetry.elapsedMillis(prepared.requestStartedNanos()),
                        response.intents(),
                        prepared.requiresGeneration(),
                        fallbackUsed
                ))
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

    private Flux<ServerSentEvent<Object>> discardAndError(
            PreparedChatTurn prepared,
            Throwable error,
            boolean partial
    ) {
        log.warn(
                "chat_request_failed requestId={} totalMs={} stage=STREAM providerStatus={} partial={} transport=SSE",
                prepared.requestId(),
                ChatTelemetry.elapsedMillis(prepared.requestStartedNanos()),
                AiProviderFailureClassifier.classify(error),
                partial
        );
        return Mono.fromRunnable(() -> chatOrchestrator.discardTurn(prepared))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(discardError -> Mono.empty())
                .thenMany(Flux.just(errorEvent(error, partial)));
    }

    private ServerSentEvent<Object> errorEvent(Throwable error, boolean partial) {
        ErrorCode errorCode;
        String message;
        if (error instanceof BusinessException businessException) {
            errorCode = businessException.getErrorCode();
            message = errorCode == ErrorCode.AI_SERVICE_UNAVAILABLE
                    ? AiProviderFailureClassifier.SYSTEM_ERROR_MESSAGE
                    : businessException.getMessage();
        } else if (AiProviderFailureClassifier.isQuotaExceeded(error)) {
            errorCode = ErrorCode.AI_SERVICE_UNAVAILABLE;
            message = AiProviderFailureClassifier.SYSTEM_ERROR_MESSAGE;
        } else if (error instanceof TimeoutException) {
            errorCode = ErrorCode.AI_SERVICE_UNAVAILABLE;
            message = AiProviderFailureClassifier.SYSTEM_ERROR_MESSAGE;
        } else {
            errorCode = ErrorCode.AI_SERVICE_UNAVAILABLE;
            message = AiProviderFailureClassifier.SYSTEM_ERROR_MESSAGE;
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
