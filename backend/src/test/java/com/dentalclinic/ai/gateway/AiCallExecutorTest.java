package com.dentalclinic.ai.gateway;

import com.dentalclinic.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
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
                    AiCallType.GENERATION,
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
        AiCallExecutor executor = executor(Duration.ofSeconds(1), 1, Duration.ofMillis(30));
        try {
            assertThatThrownBy(() -> executor.execute(
                    AiCallType.RAG_RETRIEVAL,
                    () -> {
                        throw new IllegalStateException("provider unavailable");
                    }
            )).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> executor.execute(
                    AiCallType.GENERATION,
                    () -> "blocked"
            )).isInstanceOf(BusinessException.class);

            Thread.sleep(50);

            assertThat(executor.execute(AiCallType.GENERATION, () -> "recovered"))
                    .isEqualTo("recovered");
            assertThat(executor.execute(AiCallType.UNDERSTANDING, () -> "healthy"))
                    .isEqualTo("healthy");
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
