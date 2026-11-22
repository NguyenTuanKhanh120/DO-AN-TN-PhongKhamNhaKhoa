package com.dentalclinic.ai.gateway;

import com.dentalclinic.ai.telemetry.ChatTelemetry;
import com.dentalclinic.common.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Service
public class AiCallExecutor {

    private static final Logger log = LoggerFactory.getLogger(AiCallExecutor.class);

    private final Duration understandingTimeout;
    private final Duration ragRetrievalTimeout;
    private final Duration generationTimeout;
    private final int failureThreshold;
    private final Duration openDuration;
    private final ExecutorService executor;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openUntilNanos = new AtomicLong();
    private final AtomicBoolean halfOpenProbe = new AtomicBoolean();

    @Autowired
    public AiCallExecutor(
            @Value("${app.ai.call-timeouts.understanding:2s}") Duration understandingTimeout,
            @Value("${app.ai.call-timeouts.rag-retrieval:3s}") Duration ragRetrievalTimeout,
            @Value("${app.ai.call-timeouts.generation:5s}") Duration generationTimeout,
            @Value("${app.ai.circuit-breaker.failure-threshold:2}") int failureThreshold,
            @Value("${app.ai.circuit-breaker.open-duration:30s}") Duration openDuration
    ) {
        this(
                understandingTimeout,
                ragRetrievalTimeout,
                generationTimeout,
                failureThreshold,
                openDuration,
                Executors.newVirtualThreadPerTaskExecutor()
        );
    }

    AiCallExecutor(
            Duration understandingTimeout,
            Duration ragRetrievalTimeout,
            Duration generationTimeout,
            int failureThreshold,
            Duration openDuration,
            ExecutorService executor
    ) {
        this.understandingTimeout = requirePositive(understandingTimeout, "understandingTimeout");
        this.ragRetrievalTimeout = requirePositive(ragRetrievalTimeout, "ragRetrievalTimeout");
        this.generationTimeout = requirePositive(generationTimeout, "generationTimeout");
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be greater than zero");
        }
        this.failureThreshold = failureThreshold;
        this.openDuration = requirePositive(openDuration, "openDuration");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    public <T> T execute(AiCallType callType, Supplier<T> action) {
        Objects.requireNonNull(callType, "callType must not be null");
        Objects.requireNonNull(action, "action must not be null");
        ensureAvailable(callType);
        String requestId = ChatTelemetry.currentRequestId();
        long startedNanos = System.nanoTime();
        Future<T> future = executor.submit(() -> {
            try (var ignored = ChatTelemetry.open(requestId)) {
                return action.get();
            }
        });
        try {
            T result = future.get(
                    timeoutFor(callType).toNanos(),
                    TimeUnit.NANOSECONDS
            );
            recordSuccess(callType);
            return result;
        } catch (TimeoutException exception) {
            future.cancel(true);
            recordFailure(callType, exception);
            log.warn(
                    "ai_call_timed_out requestId={} callType={} durationMs={}",
                    requestId,
                    callType,
                    ChatTelemetry.elapsedMillis(startedNanos)
            );
            throw new RuntimeException(callType + " timed out", exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            InterruptedIOException interrupted = new InterruptedIOException(
                    callType + " interrupted"
            );
            interrupted.initCause(exception);
            recordFailure(callType, interrupted);
            throw new RuntimeException(interrupted);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            recordFailure(callType, cause);
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        }
    }

    public void ensureAvailable(AiCallType callType) {
        Objects.requireNonNull(callType, "callType must not be null");
        long openUntil = openUntilNanos.get();
        if (openUntil == 0L) {
            return;
        }
        long now = System.nanoTime();
        if (now < openUntil || !halfOpenProbe.compareAndSet(false, true)) {
            log.warn(
                    "ai_call_rejected requestId={} callType={} reason=CIRCUIT_OPEN",
                    ChatTelemetry.currentRequestId(),
                    callType
            );
            throw unavailable();
        }
        log.info(
                "ai_circuit_half_open requestId={} callType={}",
                ChatTelemetry.currentRequestId(),
                callType
        );
    }

    public void recordSuccess(AiCallType callType) {
        Objects.requireNonNull(callType, "callType must not be null");
        boolean wasOpen = openUntilNanos.getAndSet(0L) != 0L;
        consecutiveFailures.set(0);
        halfOpenProbe.set(false);
        if (wasOpen) {
            log.info(
                    "ai_circuit_closed requestId={} callType={}",
                    ChatTelemetry.currentRequestId(),
                    callType
            );
        }
    }

    public void recordFailure(AiCallType callType, Throwable failure) {
        Objects.requireNonNull(callType, "callType must not be null");
        if (containsCircuitOpenException(failure)) {
            return;
        }
        AiProviderFailureClassifier.FailureType failureType =
                AiProviderFailureClassifier.classify(failure);
        if (failureType == AiProviderFailureClassifier.FailureType.BAD_REQUEST) {
            recordSuccess(callType);
            return;
        }
        int failures = consecutiveFailures.incrementAndGet();
        boolean openImmediately = failureType
                == AiProviderFailureClassifier.FailureType.QUOTA_EXCEEDED
                || failureType == AiProviderFailureClassifier.FailureType.AUTHENTICATION;
        if (openImmediately || halfOpenProbe.get() || failures >= failureThreshold) {
            openUntilNanos.set(System.nanoTime() + openDuration.toNanos());
            halfOpenProbe.set(false);
            log.warn(
                    "ai_circuit_opened requestId={} callType={} providerStatus={} failures={} openDurationMs={}",
                    ChatTelemetry.currentRequestId(),
                    callType,
                    failureType,
                    failures,
                    openDuration.toMillis()
            );
        }
    }

    public void recordCancellation(AiCallType callType) {
        Objects.requireNonNull(callType, "callType must not be null");
        if (halfOpenProbe.get()) {
            recordFailure(
                    callType,
                    new java.util.concurrent.CancellationException("AI recovery probe cancelled")
            );
        }
    }

    @PreDestroy
    void close() {
        executor.close();
    }

    private Duration timeoutFor(AiCallType callType) {
        return switch (callType) {
            case UNDERSTANDING -> understandingTimeout;
            case RAG_RETRIEVAL -> ragRetrievalTimeout;
            case GENERATION, STREAMING -> generationTimeout;
        };
    }

    private static Duration requirePositive(Duration duration, String fieldName) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }
        return duration;
    }

    private static boolean containsCircuitOpenException(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof AiCircuitOpenException) {
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

    private static BusinessException unavailable() {
        return new AiCircuitOpenException();
    }
}
