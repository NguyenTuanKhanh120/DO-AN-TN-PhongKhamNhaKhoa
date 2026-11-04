package com.dentalclinic.ai.knowledge;

import com.dentalclinic.ai.knowledge.exception.KnowledgeBaseException;
import com.dentalclinic.ai.knowledge.mapper.KnowledgeRetrievalResultMapper;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalCandidate;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeRetrievalResultMapperTest {

    private final KnowledgeRetrievalResultMapper mapper = new KnowledgeRetrievalResultMapper(
            new ObjectMapper()
    );

    @Test
    void shouldMapContentMetadataAndSourceTraceWithoutEmbedding() {
        UUID documentId = UUID.randomUUID();
        KnowledgeRetrievalCandidate candidate = new KnowledgeRetrievalCandidate(
                documentId,
                "Nội dung nha khoa",
                """
                        {
                          "sourceKey":"nidcr-dental-knowledge-vi",
                          "sourceVersion":"1.0.0",
                          "recordId":"NIDCR-VI-001",
                          "title":"Sâu răng",
                          "category":"DENTAL_CONDITION",
                          "language":"vi",
                          "symptoms":["đau răng"],
                          "serviceCategoryHints":["khám nha khoa tổng quát"],
                          "specializationHints":["nha khoa tổng quát"],
                          "sourceUrl":"https://www.nidcr.nih.gov/health-info/tooth-decay"
                        }
                        """,
                0.91
        );

        KnowledgeRetrievalResult result = mapper.toResult(candidate);

        assertThat(result.documentId()).isEqualTo(documentId);
        assertThat(result.content()).isEqualTo("Nội dung nha khoa");
        assertThat(result.score()).isEqualTo(0.91);
        assertThat(result.serviceCategoryHints()).containsExactly("khám nha khoa tổng quát");
        assertThat(result.source().recordId()).isEqualTo("NIDCR-VI-001");
        assertThat(result.source().title()).isEqualTo("Sâu răng");
        assertThat(result.source().sourceUrl())
                .isEqualTo("https://www.nidcr.nih.gov/health-info/tooth-decay");
    }

    @Test
    void shouldRejectMetadataWithoutRequiredSourceTrace() {
        KnowledgeRetrievalCandidate candidate = new KnowledgeRetrievalCandidate(
                UUID.randomUUID(),
                "Nội dung nha khoa",
                "{\"category\":\"DENTAL_CONDITION\"}",
                0.9
        );

        assertThatThrownBy(() -> mapper.toResult(candidate))
                .isInstanceOf(KnowledgeBaseException.class)
                .hasMessage("Metadata vector kiến thức không hợp lệ");
    }
}
