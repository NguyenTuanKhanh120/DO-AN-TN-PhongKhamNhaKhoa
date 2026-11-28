package com.dentalclinic.ai.gateway;

import com.google.genai.errors.ClientException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiCallExecutorTest {

    @Test
    void shouldReturnProviderResultWithinBudget() {
        AiCallExecutor executor = executor(Duration.ofSeconds(1), 2, Duration.ofSeconds(1));
        try {
            String result = executor.execute(AiCallType.GENERATION, () -> "answer");

            assertThat(result).isEqualTo("answer");
        } finally {
            executor.close();
        }
    }

    @Test
    void shouldCancelAndClassifyCallThatExceedsBudget() {
        AiCallExecutor executor = executor(Duration.ofMillis(30), 2, Duration.ofSeconds(1));
        try {
            assertThatThrownBy(() -> executor.execute(AiCallType.GENERATION, () -> {
                try {
                    Thread.sleep(500);
                    return "late answer";
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(exception);
                }
            })).satisfies(error -> assertThat(AiProviderFailureClassifier.classify(error))
                    .isEqualTo(AiProviderFailureClassifier.FailureType.TIMEOUT));
        } finally {
            executor.close();
        }
    }

    @Test
    void shouldOpenCircuitAndRejectFurtherProviderCalls() {
        AiCallExecutor executor = executor(Duration.ofSeconds(1), 2, Duration.ofSeconds(1));
        AtomicInteger providerCalls = new AtomicInteger();
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                assertThatThrownBy(() -> executor.execute(AiCallType.UNDERSTANDING, () -> {
                    providerCalls.incrementAndGet();
                    throw new IllegalStateException("provider unavailable");
                })).isInstanceOf(IllegalStateException.class);
            }

            assertThatThrownBy(() -> executor.execute(
                    AiCallType.UNDERSTANDING,
                    () -> {
                        providerCalls.incrementAndGet();
                        return "must not run";
                    }
            )).isInstanceOf(AiCircuitOpenException.class);
            assertThat(providerCalls).hasValue(2);
        } finally {
            executor.close();
        }
    }

    @Test
    void shouldAllowOneRecoveryProbeAndCloseCircuitAfterSuccess() throws Exception {
        AiCallExecutor executor = executor(Duration.ofSeconds(5), 1, Duration.ofNanos(1));
        CountDownLatch probeStarted = new CountDownLatch(1);
        CountDownLatch finishProbe = new CountDownLatch(1);
        try {
            assertThatThrownBy(() -> executor.execute(
                    AiCallType.RAG_RETRIEVAL,
                    () -> {
                        throw new IllegalStateException("provider unavailable");
                    }
            )).isInstanceOf(IllegalStateException.class);
            try (var probes = Executors.newVirtualThreadPerTaskExecutor()) {
                var recovery = probes.submit(() -> executor.execute(AiCallType.RAG_RETRIEVAL, () -> {
                    probeStarted.countDown();
                    try {
                        if (!finishProbe.await(3, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Recovery probe was not released");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                    return "recovered";
                }));
                try {
                    assertThat(probeStarted.await(2, TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> executor.execute(AiCallType.RAG_RETRIEVAL, () -> "blocked"))
                            .isInstanceOf(AiCircuitOpenException.class);
                    assertThat(executor.execute(AiCallType.GENERATION, () -> "healthy"))
                            .isEqualTo("healthy");
                    assertThatThrownBy(() -> executor.ensureAvailable(AiCallType.RAG_RETRIEVAL))
                            .isInstanceOf(AiCircuitOpenException.class);
                } finally {
                    finishProbe.countDown();
                }
                assertThat(recovery.get(2, TimeUnit.SECONDS)).isEqualTo("recovered");
                assertThat(executor.execute(AiCallType.RAG_RETRIEVAL, () -> "healthy"))
                        .isEqualTo("healthy");
            }
        } finally {
            finishProbe.countDown();
            executor.close();
        }
    }

    @ParameterizedTest
    @EnumSource(AiCallType.class)
    void shouldIsolateOpenCircuitAndSuccessesForEachOperation(AiCallType failingOperation) {
        AiCallExecutor executor = executor(Duration.ofSeconds(1), 2, Duration.ofSeconds(30));
        AtomicInteger rejectedProviderCalls = new AtomicInteger();
        try {
            executor.recordFailure(failingOperation, new ClientException(429, "RESOURCE_EXHAUSTED", "quota exceeded"));
            for (AiCallType otherOperation : AiCallType.values()) {
                if (otherOperation != failingOperation) {
                    assertThat(executor.execute(otherOperation, () -> "healthy")).isEqualTo("healthy");
                }
            }
            assertThatThrownBy(() -> executor.execute(failingOperation, () -> {
                rejectedProviderCalls.incrementAndGet();
                return "must not run";
            })).isInstanceOf(AiCircuitOpenException.class);
            assertThat(rejectedProviderCalls).hasValue(0);
        } finally {
            executor.close();
        }
    }

    @Test
    void shouldNotCombineFailureCountsFromDifferentOperations() {
        AiCallExecutor executor = executor(Duration.ofSeconds(1), 2, Duration.ofSeconds(30));
        try {
            executor.recordFailure(AiCallType.STREAMING, new TimeoutException());
            executor.recordFailure(AiCallType.RAG_RETRIEVAL, new TimeoutException());
            executor.ensureAvailable(AiCallType.STREAMING);
            executor.ensureAvailable(AiCallType.RAG_RETRIEVAL);

            executor.recordFailure(AiCallType.STREAMING, new TimeoutException());
            assertThatThrownBy(() -> executor.ensureAvailable(AiCallType.STREAMING))
                    .isInstanceOf(AiCircuitOpenException.class);
            executor.ensureAvailable(AiCallType.RAG_RETRIEVAL);

            executor.recordFailure(AiCallType.RAG_RETRIEVAL, new TimeoutException());
            assertThatThrownBy(() -> executor.ensureAvailable(AiCallType.RAG_RETRIEVAL))
                    .isInstanceOf(AiCircuitOpenException.class);
        } finally {
            executor.close();
        }
    }

    @Test
    void shouldNotResetStreamingFailuresWhenRagSucceeds() {
        AiCallExecutor executor = executor(Duration.ofSeconds(1), 2, Duration.ofSeconds(30));
        try {
            executor.recordFailure(AiCallType.STREAMING, new TimeoutException());
            assertThat(executor.execute(AiCallType.RAG_RETRIEVAL, () -> "evidence")).isEqualTo("evidence");
            executor.recordFailure(AiCallType.STREAMING, new TimeoutException());

            assertThatThrownBy(() -> executor.ensureAvailable(AiCallType.STREAMING))
                    .isInstanceOf(AiCircuitOpenException.class);
            assertThat(executor.execute(AiCallType.RAG_RETRIEVAL, () -> "more evidence"))
                    .isEqualTo("more evidence");
        } finally {
            executor.close();
        }
    }

    @Test
    void shouldIsolateRecoveryProbeSuccessFailureAndCancellation() {
        AiCallExecutor executor = executor(Duration.ofSeconds(1), 1, Duration.ofNanos(1));
        try {
            executor.recordFailure(AiCallType.STREAMING, new TimeoutException());
            executor.recordFailure(AiCallType.RAG_RETRIEVAL, new TimeoutException());
            executor.ensureAvailable(AiCallType.STREAMING);
            executor.ensureAvailable(AiCallType.RAG_RETRIEVAL);

            executor.recordCancellation(AiCallType.UNDERSTANDING);
            executor.recordFailure(AiCallType.GENERATION, new ClientException(400, "INVALID_ARGUMENT", "invalid request"));
            assertThatThrownBy(() -> executor.ensureAvailable(AiCallType.STREAMING))
                    .isInstanceOf(AiCircuitOpenException.class);
            assertThatThrownBy(() -> executor.ensureAvailable(AiCallType.RAG_RETRIEVAL))
                    .isInstanceOf(AiCircuitOpenException.class);

            executor.recordSuccess(AiCallType.RAG_RETRIEVAL);
            assertThat(executor.execute(AiCallType.RAG_RETRIEVAL, () -> "recovered"))
                    .isEqualTo("recovered");
            assertThatThrownBy(() -> executor.ensureAvailable(AiCallType.STREAMING))
                    .isInstanceOf(AiCircuitOpenException.class);

            executor.recordCancellation(AiCallType.STREAMING);
            executor.ensureAvailable(AiCallType.STREAMING);
            assertThatThrownBy(() -> executor.ensureAvailable(AiCallType.STREAMING))
                    .isInstanceOf(AiCircuitOpenException.class);
            executor.recordSuccess(AiCallType.STREAMING);
            executor.ensureAvailable(AiCallType.STREAMING);
        } finally {
            executor.close();
        }
    }

    private AiCallExecutor executor(
            Duration operationTimeout,
            int failureThreshold,
            Duration openDuration
    ) {
        return new AiCallExecutor(
                operationTimeout,
                operationTimeout,
                operationTimeout,
                failureThreshold,
                openDuration
        );
    }
}
