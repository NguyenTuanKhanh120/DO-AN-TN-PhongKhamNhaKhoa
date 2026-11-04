package com.dentalclinic.ai.config;

import com.dentalclinic.ai.embedding.EmbeddingGateway;
import com.dentalclinic.ai.embedding.GoogleGenAiEmbeddingGateway;
import com.dentalclinic.ai.gateway.AiChatGateway;
import com.dentalclinic.ai.gateway.AiStreamingChatGateway;
import com.dentalclinic.ai.gateway.SpringAiChatGateway;
import com.dentalclinic.ai.gateway.SpringAiStreamingChatGateway;
import com.dentalclinic.ai.knowledge.constant.KnowledgeEmbeddingConstants;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
public class AiConfiguration {

    private static final int MAX_RETRY_ATTEMPTS = 10;

    @Bean(destroyMethod = "close")
    Client geminiClient(AiProperties properties) {
        validate(properties);

        HttpRetryOptions retryOptions = HttpRetryOptions.builder()
                .attempts(properties.retry().maxAttempts())
                .initialDelay(toSeconds(properties.retry().initialDelay()))
                .maxDelay(toSeconds(properties.retry().maxDelay()))
                .build();

        HttpOptions httpOptions = HttpOptions.builder()
                .timeout(Math.toIntExact(properties.requestTimeout().toMillis()))
                .retryOptions(retryOptions)
                .build();

        return Client.builder()
                .apiKey(properties.apiKey().trim())
                .httpOptions(httpOptions)
                .build();
    }

    @Bean
    GoogleGenAiChatModel geminiChatModel(AiProperties properties, Client client) {
        GoogleGenAiChatOptions chatOptions = GoogleGenAiChatOptions.builder()
                .model(properties.chatModel().trim())
                .temperature(properties.temperature())
                .maxOutputTokens(properties.maxOutputTokens())
                .thinkingBudget(properties.thinkingBudget())
                .includeThoughts(false)
                .build();

        return GoogleGenAiChatModel.builder()
                .genAiClient(client)
                .defaultOptions(chatOptions)
                // Retry is handled by the Google client above; avoid multiplying attempts.
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).noBackoff().build())
                .build();
    }

    @Bean
    EmbeddingGateway embeddingGateway(Client client, AiProperties properties) {
        return new GoogleGenAiEmbeddingGateway(client.models, properties);
    }

    @Bean
    AiChatGateway aiChatGateway(ChatModel chatModel) {
        return new SpringAiChatGateway(chatModel);
    }

    @Bean
    AiStreamingChatGateway aiStreamingChatGateway(GoogleGenAiChatModel chatModel) {
        return new SpringAiStreamingChatGateway(chatModel);
    }

    private static void validate(AiProperties properties) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new IllegalStateException("GEMINI_API_KEY is required when AI_ENABLED=true");
        }
        if (!StringUtils.hasText(properties.chatModel())) {
            throw new IllegalStateException("GEMINI_CHAT_MODEL must not be blank");
        }
        if (!StringUtils.hasText(properties.embeddingModel())) {
            throw new IllegalStateException("GEMINI_EMBEDDING_MODEL must not be blank");
        }
        if (properties.embeddingDimension() != KnowledgeEmbeddingConstants.VECTOR_DIMENSION) {
            throw new IllegalStateException("GEMINI_EMBEDDING_DIMENSION must be 768");
        }
        if (!Double.isFinite(properties.temperature())
                || properties.temperature() < 0.0
                || properties.temperature() > 2.0) {
            throw new IllegalStateException("GEMINI_CHAT_TEMPERATURE must be between 0.0 and 2.0");
        }
        if (properties.maxOutputTokens() <= 0) {
            throw new IllegalStateException("GEMINI_MAX_OUTPUT_TOKENS must be greater than zero");
        }
        if (properties.thinkingBudget() < 0
                || properties.thinkingBudget() >= properties.maxOutputTokens()) {
            throw new IllegalStateException(
                    "GEMINI_THINKING_BUDGET must be non-negative and less than GEMINI_MAX_OUTPUT_TOKENS"
            );
        }
        requirePositiveDuration(properties.requestTimeout(), "GEMINI_REQUEST_TIMEOUT");
        if (properties.requestTimeout().toMillis() > Integer.MAX_VALUE) {
            throw new IllegalStateException("GEMINI_REQUEST_TIMEOUT is too large");
        }
        if (properties.retry() == null) {
            throw new IllegalStateException("Gemini retry configuration is required");
        }
        if (properties.retry().maxAttempts() < 1 || properties.retry().maxAttempts() > MAX_RETRY_ATTEMPTS) {
            throw new IllegalStateException("GEMINI_RETRY_MAX_ATTEMPTS must be between 1 and 10");
        }
        requireNonNegativeDuration(properties.retry().initialDelay(), "GEMINI_RETRY_INITIAL_DELAY");
        requireNonNegativeDuration(properties.retry().maxDelay(), "GEMINI_RETRY_MAX_DELAY");
        if (properties.retry().maxDelay().compareTo(properties.retry().initialDelay()) < 0) {
            throw new IllegalStateException("GEMINI_RETRY_MAX_DELAY must not be less than the initial delay");
        }
    }

    private static void requirePositiveDuration(Duration value, String propertyName) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(propertyName + " must be greater than zero");
        }
    }

    private static void requireNonNegativeDuration(Duration value, String propertyName) {
        if (value == null || value.isNegative()) {
            throw new IllegalStateException(propertyName + " must not be negative");
        }
    }

    private static double toSeconds(Duration duration) {
        return duration.toNanos() / 1_000_000_000.0;
    }
}
