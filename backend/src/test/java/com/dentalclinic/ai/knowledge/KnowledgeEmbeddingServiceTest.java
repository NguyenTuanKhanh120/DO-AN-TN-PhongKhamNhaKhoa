package com.dentalclinic.ai.knowledge;

import com.dentalclinic.ai.embedding.EmbeddingGateway;
import com.dentalclinic.ai.knowledge.exception.KnowledgeBaseException;
import com.dentalclinic.ai.knowledge.model.DentalKnowledgeDataset;
import com.dentalclinic.ai.knowledge.model.EmbeddedKnowledgeChunk;
import com.dentalclinic.ai.knowledge.model.KnowledgeChunk;
import com.dentalclinic.ai.knowledge.model.KnowledgeChunkMetadata;
import com.dentalclinic.ai.knowledge.service.KnowledgeChunker;
import com.dentalclinic.ai.knowledge.service.KnowledgeEmbeddingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeEmbeddingServiceTest {

    private static final int DIMENSION = 768;

    private final KnowledgeChunker chunker = mock(KnowledgeChunker.class);
    private final EmbeddingGateway embeddingGateway = mock(EmbeddingGateway.class);
    private final KnowledgeEmbeddingService service = new KnowledgeEmbeddingService(
            chunker,
            Optional.of(embeddingGateway)
    );

    @Test
    void shouldEmbedChunkContentWithoutAddingMetadataToEmbeddingText() {
        DentalKnowledgeDataset dataset = mock(DentalKnowledgeDataset.class);
        List<KnowledgeChunk> chunks = List.of(chunk("Nội dung một", "NIDCR-VI-001"));
        when(chunker.chunk(dataset)).thenReturn(chunks);
        when(embeddingGateway.embedAll(any())).thenReturn(List.of(new float[DIMENSION]));
        when(embeddingGateway.modelName()).thenReturn("gemini-embedding-2");
        when(embeddingGateway.dimension()).thenReturn(DIMENSION);

        List<EmbeddedKnowledgeChunk> result = service.embed(dataset);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().chunk()).isSameAs(chunks.getFirst());
        assertThat(result.getFirst().embeddingModel()).isEqualTo("gemini-embedding-2");
        assertThat(result.getFirst().embeddingDimension()).isEqualTo(DIMENSION);
        ArgumentCaptor<List<String>> contentCaptor = ArgumentCaptor.forClass(List.class);
        verify(embeddingGateway).embedAll(contentCaptor.capture());
        assertThat(contentCaptor.getValue()).containsExactly("Nội dung một");
    }

    @Test
    void shouldRejectEmbeddingCountMismatch() {
        DentalKnowledgeDataset dataset = mock(DentalKnowledgeDataset.class);
        when(chunker.chunk(dataset)).thenReturn(List.of(chunk("Một", "NIDCR-VI-001")));
        when(embeddingGateway.embedAll(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.embed(dataset))
                .isInstanceOf(KnowledgeBaseException.class)
                .hasMessageContaining("không khớp số chunk");
    }

    @Test
    void shouldRejectEmbeddingWithWrongDimensionFromAnyGatewayImplementation() {
        DentalKnowledgeDataset dataset = mock(DentalKnowledgeDataset.class);
        when(chunker.chunk(dataset)).thenReturn(List.of(chunk("Một", "NIDCR-VI-001")));
        when(embeddingGateway.embedAll(any())).thenReturn(List.of(new float[10]));
        when(embeddingGateway.dimension()).thenReturn(DIMENSION);

        assertThatThrownBy(() -> service.embed(dataset))
                .isInstanceOf(KnowledgeBaseException.class)
                .hasMessageContaining("không đúng 768 chiều");
    }

    @Test
    void shouldRejectEmbeddingWhenAiIsDisabled() {
        KnowledgeEmbeddingService disabledService = new KnowledgeEmbeddingService(
                chunker,
                Optional.empty()
        );

        assertThatThrownBy(() -> disabledService.embed(mock(DentalKnowledgeDataset.class)))
                .isInstanceOf(KnowledgeBaseException.class)
                .hasMessage("Embedding chưa được bật trên hệ thống");
    }

    private KnowledgeChunk chunk(String content, String recordId) {
        return new KnowledgeChunk(
                content,
                new KnowledgeChunkMetadata(
                        "nidcr-dental-knowledge-vi",
                        "1.0.0",
                        recordId,
                        "Title",
                        "DENTAL_CONDITION",
                        "vi",
                        List.of("đau răng"),
                        List.of("khám tổng quát"),
                        List.of("nha khoa tổng quát"),
                        "https://www.nidcr.nih.gov/health-info/tooth-decay",
                        "PENDING_CLINICAL_REVIEW"
                )
        );
    }
}
