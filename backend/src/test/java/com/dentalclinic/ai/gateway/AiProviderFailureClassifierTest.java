package com.dentalclinic.ai.gateway;

import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.google.genai.errors.ClientException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class AiProviderFailureClassifierTest {

    @Test
    void shouldClassifyProviderHttpFailuresWithoutUsingProviderMessages() {
        assertThat(AiProviderFailureClassifier.classify(providerFailure(400)))
                .isEqualTo(AiProviderFailureClassifier.FailureType.BAD_REQUEST);
        assertThat(AiProviderFailureClassifier.classify(providerFailure(401)))
                .isEqualTo(AiProviderFailureClassifier.FailureType.AUTHENTICATION);
        assertThat(AiProviderFailureClassifier.classify(providerFailure(429)))
                .isEqualTo(AiProviderFailureClassifier.FailureType.QUOTA_EXCEEDED);
        assertThat(AiProviderFailureClassifier.classify(providerFailure(503)))
                .isEqualTo(AiProviderFailureClassifier.FailureType.UNAVAILABLE);
    }

    @Test
    void shouldClassifyNestedTimeoutAndAiServiceFailures() {
        assertThat(AiProviderFailureClassifier.classify(
                new IllegalStateException("outer", new TimeoutException("slow"))
        )).isEqualTo(AiProviderFailureClassifier.FailureType.TIMEOUT);
        assertThat(AiProviderFailureClassifier.classify(new BusinessException(
                ErrorCode.AI_SERVICE_UNAVAILABLE,
                "safe public message"
        ))).isEqualTo(AiProviderFailureClassifier.FailureType.AI_SERVICE_UNAVAILABLE);
    }

    @Test
    void shouldPreserveQuotaCompatibilityHelper() {
        assertThat(AiProviderFailureClassifier.isQuotaExceeded(providerFailure(429))).isTrue();
        assertThat(AiProviderFailureClassifier.isQuotaExceeded(providerFailure(503))).isFalse();
    }

    private ClientException providerFailure(int statusCode) {
        return new ClientException(statusCode, "PROVIDER_STATUS", "provider detail");
    }
}
