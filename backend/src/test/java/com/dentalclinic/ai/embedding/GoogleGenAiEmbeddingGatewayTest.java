package com.dentalclinic.ai.embedding;

import com.dentalclinic.ai.config.AiProperties;
import com.dentalclinic.ai.knowledge.exception.KnowledgeBaseException;
import com.google.genai.Models;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleGenAiEmbeddingGatewayTest {

    private static final int DIMENSION = 768;

    private final Models models = mock(Models.class);
    private final GoogleGenAiEmbeddingGateway gateway = new GoogleGenAiEmbeddingGateway(
            models,
            properties()
    );

    @Test
    void shouldRequestConfiguredDimensionAndNormalizeReturnedVectors() {
        when(models.embedContent(eq("gemini-embedding-2"), anyList(), any(EmbedContentConfig.class)))
                .thenReturn(response(vector(DIMENSION, 2.0f), vector(DIMENSION, 3.0f)));

        List<float[]> embeddings = gateway.embedAll(List.of(" Nội dung một ", "Nội dung hai"));

        assertThat(embeddings).hasSize(2);
        assertThat(embeddings).allSatisfy(embedding -> {
            assertThat(embedding).hasSize(DIMENSION);
            assertThat(l2Norm(embedding)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.00001));
        });
        assertThat(gateway.modelName()).isEqualTo("gemini-embedding-2");
        assertThat(gateway.dimension()).isEqualTo(DIMENSION);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> textsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<EmbedContentConfig> configCaptor = ArgumentCaptor.forClass(EmbedContentConfig.class);
        verify(models).embedContent(eq("gemini-embedding-2"), textsCaptor.capture(), configCaptor.capture());
        assertThat(textsCaptor.getValue()).containsExactly("Nội dung một", "Nội dung hai");
        assertThat(configCaptor.getValue().outputDimensionality()).contains(DIMENSION);
    }

    @Test
    void shouldRejectUnexpectedEmbeddingDimension() {
        when(models.embedContent(eq("gemini-embedding-2"), anyList(), any(EmbedContentConfig.class)))
                .thenReturn(response(vector(DIMENSION - 1, 1.0f)));

        assertThatThrownBy(() -> gateway.embedAll(List.of("Nội dung")))
                .isInstanceOf(KnowledgeBaseException.class)
                .hasMessageContaining("phải có đúng 768 chiều");
    }

    @Test
    void shouldRejectNonFiniteEmbeddingValue() {
        List<Float> values = vector(DIMENSION, 1.0f);
        values.set(10, Float.NaN);
        when(models.embedContent(eq("gemini-embedding-2"), anyList(), any(EmbedContentConfig.class)))
                .thenReturn(response(values));

        assertThatThrownBy(() -> gateway.embedAll(List.of("Nội dung")))
                .isInstanceOf(KnowledgeBaseException.class)
                .hasMessageContaining("không hữu hạn");
    }

    @Test
    void shouldRejectBlankInputBeforeCallingGemini() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> gateway.embedAll(List.of("  ")))
                .withMessage("Embedding input at index 0 must not be blank");
    }

    private EmbedContentResponse response(List<Float>... vectors) {
        List<ContentEmbedding> embeddings = new ArrayList<>(vectors.length);
        for (List<Float> vector : vectors) {
            embeddings.add(ContentEmbedding.builder().values(vector).build());
        }
        return EmbedContentResponse.builder().embeddings(embeddings).build();
    }

    private List<Float> vector(int dimension, float value) {
        return new ArrayList<>(java.util.Collections.nCopies(dimension, value));
    }

    private double l2Norm(float[] vector) {
        double sum = 0.0;
        for (float value : vector) {
            sum += (double) value * value;
        }
        return Math.sqrt(sum);
    }

    private AiProperties properties() {
        return new AiProperties(
                true,
                "test-key",
                "gemini-3.5-flash",
                "gemini-embedding-2",
                DIMENSION,
                0.2,
                1024,
                256,
                Duration.ofSeconds(30),
                new AiProperties.RetryProperties(3, Duration.ofMillis(500), Duration.ofSeconds(4))
        );
    }
}
