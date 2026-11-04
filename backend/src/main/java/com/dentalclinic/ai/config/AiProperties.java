package com.dentalclinic.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        boolean enabled,
        String apiKey,
        String chatModel,
        String embeddingModel,
        int embeddingDimension,
        double temperature,
        int maxOutputTokens,
        int thinkingBudget,
        Duration requestTimeout,
        RetryProperties retry
) {

    public record RetryProperties(
            int maxAttempts,
            Duration initialDelay,
            Duration maxDelay
    ) {
    }
}
