package com.dentalclinic.ai.gateway;

import com.google.genai.errors.ClientException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiProviderFailureClassifierTest {

    @Test
    void shouldDetectNestedGoogleQuotaFailure() {
        RuntimeException failure = new RuntimeException(
                "provider failed",
                new ClientException(429, "RESOURCE_EXHAUSTED", "quota exceeded")
        );

        assertThat(AiProviderFailureClassifier.isQuotaExceeded(failure)).isTrue();
    }

    @Test
    void shouldIgnoreOtherProviderFailures() {
        assertThat(AiProviderFailureClassifier.isQuotaExceeded(
                new ClientException(400, "INVALID_ARGUMENT", "invalid request")
        )).isFalse();
    }
}
