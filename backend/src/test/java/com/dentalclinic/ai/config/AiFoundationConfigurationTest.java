package com.dentalclinic.ai.config;

import com.dentalclinic.ai.embedding.EmbeddingGateway;
import com.dentalclinic.ai.gateway.AiChatGateway;
import com.dentalclinic.ai.gateway.AiStreamingChatGateway;
import com.google.genai.Client;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AiFoundationConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiConfiguration.class);

    @Test
    void shouldKeepAiBeansDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ChatModel.class);
            assertThat(context).doesNotHaveBean(AiChatGateway.class);
            assertThat(context).doesNotHaveBean(AiStreamingChatGateway.class);
            assertThat(context).doesNotHaveBean(EmbeddingGateway.class);
            assertThat(context).doesNotHaveBean(Client.class);
        });
    }

    @Test
    void shouldCreateGeminiFoundationWhenEnabled() {
        contextRunner
                .withPropertyValues(validProperties())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ChatModel.class);
                    assertThat(context).hasSingleBean(AiChatGateway.class);
                    assertThat(context).hasSingleBean(AiStreamingChatGateway.class);
                    assertThat(context).hasSingleBean(EmbeddingGateway.class);
                    assertThat(context).hasSingleBean(Client.class);
                });
    }

    @Test
    void shouldFailFastWithoutApiKeyWhenEnabled() {
        contextRunner
                .withPropertyValues(validProperties())
                .withPropertyValues("app.ai.api-key=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasRootCauseMessage("GEMINI_API_KEY is required when AI_ENABLED=true");
                });
    }

    @Test
    void shouldRejectEmbeddingDimensionThatDoesNotMatchVectorStore() {
        contextRunner
                .withPropertyValues(validProperties())
                .withPropertyValues("app.ai.embedding-dimension=3072")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasRootCauseMessage("GEMINI_EMBEDDING_DIMENSION must be 768");
                });
    }

    @Test
    void shouldRejectThinkingBudgetThatConsumesEntireOutputBudget() {
        contextRunner
                .withPropertyValues(validProperties())
                .withPropertyValues("app.ai.thinking-budget=1024")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasRootCauseMessage(
                                    "GEMINI_THINKING_BUDGET must be non-negative and less than "
                                            + "GEMINI_MAX_OUTPUT_TOKENS"
                            );
                });
    }

    private static String[] validProperties() {
        return new String[]{
                "app.ai.enabled=true",
                "app.ai.api-key=test-only-key",
                "app.ai.chat-model=gemini-3.5-flash",
                "app.ai.embedding-model=gemini-embedding-2",
                "app.ai.embedding-dimension=768",
                "app.ai.temperature=0.2",
                "app.ai.max-output-tokens=1024",
                "app.ai.thinking-budget=256",
                "app.ai.request-timeout=60s",
                "app.ai.retry.max-attempts=2",
                "app.ai.retry.initial-delay=500ms",
                "app.ai.retry.max-delay=4s"
        };
    }
}
