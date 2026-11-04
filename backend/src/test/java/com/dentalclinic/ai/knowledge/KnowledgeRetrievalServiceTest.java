package com.dentalclinic.ai.knowledge;

import com.dentalclinic.ai.embedding.EmbeddingGateway;
import com.dentalclinic.ai.knowledge.config.KnowledgeRetrievalProperties;
import com.dentalclinic.ai.knowledge.constant.KnowledgeEmbeddingConstants;
import com.dentalclinic.ai.knowledge.exception.KnowledgeBaseException;
import com.dentalclinic.ai.knowledge.mapper.KnowledgeRetrievalResultMapper;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalCandidate;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalCriteria;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalFilter;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalRequest;
import com.dentalclinic.ai.knowledge.repository.KnowledgeRetrievalRepository;
import com.dentalclinic.ai.knowledge.service.KnowledgeRetrievalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeRetrievalServiceTest {

    @Mock
    private EmbeddingGateway embeddingGateway;

    @Mock
    private KnowledgeRetrievalRepository repository;

    @Mock
    private KnowledgeRetrievalResultMapper mapper;

    private final KnowledgeRetrievalProperties properties = new KnowledgeRetrievalProperties(
            5,
            20,
            0.55
    );

    @Test
    void shouldEmbedQueryOnceAndApplyConfiguredDefaults() {
        float[] embedding = validEmbedding();
        KnowledgeRetrievalCandidate candidate = new KnowledgeRetrievalCandidate(
                UUID.randomUUID(),
                "content",
                "{}",
                0.8
        );
        when(embeddingGateway.modelName()).thenReturn("gemini-embedding-2");
        when(embeddingGateway.dimension()).thenReturn(KnowledgeEmbeddingConstants.VECTOR_DIMENSION);
        when(embeddingGateway.embedAll(List.of("đau răng"))).thenReturn(List.of(embedding));
        when(repository.search(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(candidate));

        KnowledgeRetrievalService service = service(Optional.of(embeddingGateway));
        service.retrieve(KnowledgeRetrievalRequest.of("  đau răng  "));

        ArgumentCaptor<KnowledgeRetrievalCriteria> criteriaCaptor = ArgumentCaptor.forClass(
                KnowledgeRetrievalCriteria.class
        );
        verify(repository).search(criteriaCaptor.capture());
        KnowledgeRetrievalCriteria criteria = criteriaCaptor.getValue();
        assertThat(criteria.topK()).isEqualTo(5);
        assertThat(criteria.minimumSimilarity()).isEqualTo(0.55);
        assertThat(criteria.embeddingModel()).isEqualTo("gemini-embedding-2");
        assertThat(criteria.queryEmbedding()).containsExactly(embedding);
        verify(embeddingGateway).embedAll(List.of("đau răng"));
    }

    @Test
    void shouldApplyRequestTopKThresholdAndMetadataFilter() {
        float[] embedding = validEmbedding();
        KnowledgeRetrievalFilter filter = new KnowledgeRetrievalFilter(
                "nidcr-dental-knowledge-vi",
                "DENTAL_CONDITION",
                "vi",
                "PENDING_CLINICAL_REVIEW"
        );
        when(embeddingGateway.modelName()).thenReturn("gemini-embedding-2");
        when(embeddingGateway.dimension()).thenReturn(KnowledgeEmbeddingConstants.VECTOR_DIMENSION);
        when(embeddingGateway.embedAll(List.of("sưng lợi"))).thenReturn(List.of(embedding));
        when(repository.search(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        service(Optional.of(embeddingGateway)).retrieve(new KnowledgeRetrievalRequest(
                "sưng lợi",
                3,
                0.72,
                filter
        ));

        ArgumentCaptor<KnowledgeRetrievalCriteria> captor = ArgumentCaptor.forClass(
                KnowledgeRetrievalCriteria.class
        );
        verify(repository).search(captor.capture());
        assertThat(captor.getValue().topK()).isEqualTo(3);
        assertThat(captor.getValue().minimumSimilarity()).isEqualTo(0.72);
        assertThat(captor.getValue().filter()).isEqualTo(filter);
    }

    @Test
    void shouldRejectTopKGreaterThanConfiguredMaximumBeforeEmbedding() {
        when(embeddingGateway.modelName()).thenReturn("gemini-embedding-2");
        when(embeddingGateway.dimension()).thenReturn(KnowledgeEmbeddingConstants.VECTOR_DIMENSION);

        assertThatThrownBy(() -> service(Optional.of(embeddingGateway)).retrieve(
                new KnowledgeRetrievalRequest("đau răng", 21, null, null)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed 20");

        verifyNoInteractions(repository);
    }

    @Test
    void shouldRejectRetrievalWhenEmbeddingIsDisabled() {
        assertThatThrownBy(() -> service(Optional.empty()).retrieve(
                KnowledgeRetrievalRequest.of("đau răng")
        ))
                .isInstanceOf(KnowledgeBaseException.class)
                .hasMessage("Embedding chưa được bật trên hệ thống");

        verifyNoInteractions(repository);
    }

    @Test
    void shouldRejectInvalidQueryEmbeddingDimension() {
        when(embeddingGateway.modelName()).thenReturn("gemini-embedding-2");
        when(embeddingGateway.dimension()).thenReturn(KnowledgeEmbeddingConstants.VECTOR_DIMENSION);
        when(embeddingGateway.embedAll(List.of("đau răng"))).thenReturn(List.of(new float[10]));

        assertThatThrownBy(() -> service(Optional.of(embeddingGateway)).retrieve(
                KnowledgeRetrievalRequest.of("đau răng")
        ))
                .isInstanceOf(KnowledgeBaseException.class)
                .hasMessageContaining("768 chiều");

        verifyNoInteractions(repository);
    }

    private KnowledgeRetrievalService service(Optional<EmbeddingGateway> gateway) {
        return new KnowledgeRetrievalService(gateway, repository, mapper, properties);
    }

    private float[] validEmbedding() {
        float[] embedding = new float[KnowledgeEmbeddingConstants.VECTOR_DIMENSION];
        embedding[0] = 1.0f;
        return embedding;
    }
}
