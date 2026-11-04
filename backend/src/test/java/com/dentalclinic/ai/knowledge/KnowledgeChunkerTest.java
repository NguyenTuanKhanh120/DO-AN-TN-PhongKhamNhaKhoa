package com.dentalclinic.ai.knowledge;

import com.dentalclinic.ai.knowledge.model.DentalKnowledgeDataset;
import com.dentalclinic.ai.knowledge.model.DentalKnowledgeRecord;
import com.dentalclinic.ai.knowledge.model.DentalKnowledgeSource;
import com.dentalclinic.ai.knowledge.model.KnowledgeChunk;
import com.dentalclinic.ai.knowledge.service.KnowledgeChunker;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeChunkerTest {

    private final KnowledgeChunker chunker = new KnowledgeChunker();

    @Test
    void shouldCreateExactlyOneChunkPerRecordUsingOnlyContentAsEmbeddingText() {
        DentalKnowledgeDataset dataset = dataset();

        List<KnowledgeChunk> chunks = chunker.chunk(dataset);

        assertThat(chunks).hasSize(2);
        assertThat(chunks).extracting(KnowledgeChunk::content)
                .containsExactly("Nội dung record 1", "Nội dung record 2");
        assertThat(chunks.getFirst().metadata().sourceKey()).isEqualTo("nidcr-dental-knowledge-vi");
        assertThat(chunks.getFirst().metadata().sourceVersion()).isEqualTo("1.0.0");
        assertThat(chunks.getFirst().metadata().recordId()).isEqualTo("NIDCR-VI-001");
        assertThat(chunks.getFirst().metadata().title()).isEqualTo("Sâu răng");
        assertThat(chunks.getFirst().metadata().category()).isEqualTo("DENTAL_CONDITION");
        assertThat(chunks.getFirst().metadata().language()).isEqualTo("vi");
        assertThat(chunks.getFirst().metadata().symptoms()).containsExactly("đau răng");
        assertThat(chunks.getFirst().metadata().serviceCategoryHints()).containsExactly("khám tổng quát");
        assertThat(chunks.getFirst().metadata().specializationHints()).containsExactly("nha khoa tổng quát");
        assertThat(chunks.getFirst().metadata().sourceUrl())
                .isEqualTo("https://www.nidcr.nih.gov/health-info/tooth-decay");
        assertThat(chunks.getFirst().metadata().clinicalReviewStatus())
                .isEqualTo("PENDING_CLINICAL_REVIEW");
    }

    private DentalKnowledgeDataset dataset() {
        List<DentalKnowledgeRecord> records = List.of(
                record("NIDCR-VI-001", "tooth-decay", "Sâu răng", "Nội dung record 1"),
                record("NIDCR-VI-002", "gum-disease", "Bệnh nướu", "Nội dung record 2")
        );
        return new DentalKnowledgeDataset(
                "nidcr-dental-knowledge-vi",
                "1.0.0",
                "Dataset",
                "Description",
                "vi",
                "en",
                LocalDate.of(2026, 10, 28),
                records.size(),
                "content",
                "PENDING_CLINICAL_REVIEW",
                "AI_ASSISTED_PARAPHRASE",
                "Curation note",
                true,
                List.of("RAG_RETRIEVAL"),
                List.of("PATIENT_DATA"),
                records
        );
    }

    private DentalKnowledgeRecord record(String id, String slug, String title, String content) {
        return new DentalKnowledgeRecord(
                id,
                slug,
                title,
                "DENTAL_CONDITION",
                content,
                List.of("đau răng"),
                List.of("mảng bám"),
                List.of("vệ sinh răng miệng"),
                List.of("khám tổng quát"),
                List.of("nha khoa tổng quát"),
                List.of("răng"),
                new DentalKnowledgeSource(
                        "NIDCR",
                        "NIH",
                        title,
                        URI.create("https://www.nidcr.nih.gov/health-info/" + slug),
                        LocalDate.of(2026, 10, 28),
                        "OFFICIAL_HEALTH_INFORMATION"
                )
        );
    }
}
