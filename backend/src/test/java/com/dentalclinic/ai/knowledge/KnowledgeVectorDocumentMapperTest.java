package com.dentalclinic.ai.knowledge;

import com.dentalclinic.ai.knowledge.entity.KnowledgeVectorDocument;
import com.dentalclinic.ai.knowledge.mapper.KnowledgeVectorDocumentMapper;
import com.dentalclinic.ai.knowledge.model.EmbeddedKnowledgeChunk;
import com.dentalclinic.ai.knowledge.model.KnowledgeChunk;
import com.dentalclinic.ai.knowledge.model.KnowledgeChunkMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeVectorDocumentMapperTest {

    private final KnowledgeVectorDocumentMapper mapper = new KnowledgeVectorDocumentMapper(
            new ObjectMapper()
    );

    @Test
    void shouldMapContentVectorAndTraceableMetadata() {
        float[] vector = new float[768];
        vector[0] = 1.0f;
        EmbeddedKnowledgeChunk embeddedChunk = new EmbeddedKnowledgeChunk(
                chunk(),
                "gemini-embedding-2",
                768,
                vector
        );

        UUID syncId = UUID.randomUUID();
        KnowledgeVectorDocument entity = mapper.toStagingEntity(
                embeddedChunk,
                "a".repeat(64),
                syncId
        );

        assertThat(entity.getContent()).isEqualTo("Nội dung embedding");
        assertThat(entity.getEmbedding()).hasSize(768);
        assertThat(entity.getMetadata().path("sourceKey").asText())
                .isEqualTo("nidcr-dental-knowledge-vi");
        assertThat(entity.getMetadata().path("sourceVersion").asText()).isEqualTo("1.0.0");
        assertThat(entity.getMetadata().path("recordId").asText()).isEqualTo("NIDCR-VI-001");
        assertThat(entity.getMetadata().path("sourceUrl").asText())
                .isEqualTo("https://www.nidcr.nih.gov/health-info/tooth-decay");
        assertThat(entity.getMetadata().path("clinicalReviewStatus").asText())
                .isEqualTo("PENDING_CLINICAL_REVIEW");
        assertThat(entity.getMetadata().path("embeddingModel").asText())
                .isEqualTo("gemini-embedding-2");
        assertThat(entity.getMetadata().path("embeddingDimension").asInt()).isEqualTo(768);
        assertThat(entity.getMetadata().path("contentHash").asText()).isEqualTo("a".repeat(64));
        assertThat(entity.getMetadata().path("syncId").asText()).isEqualTo(syncId.toString());
    }

    private KnowledgeChunk chunk() {
        return new KnowledgeChunk(
                "Nội dung embedding",
                new KnowledgeChunkMetadata(
                        "nidcr-dental-knowledge-vi",
                        "1.0.0",
                        "NIDCR-VI-001",
                        "Sâu răng",
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
