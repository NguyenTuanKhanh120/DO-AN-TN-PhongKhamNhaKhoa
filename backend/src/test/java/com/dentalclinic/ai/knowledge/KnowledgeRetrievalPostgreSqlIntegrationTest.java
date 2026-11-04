package com.dentalclinic.ai.knowledge;

import com.dentalclinic.ai.knowledge.constant.KnowledgeEmbeddingConstants;
import com.dentalclinic.ai.knowledge.entity.KnowledgeFileType;
import com.dentalclinic.ai.knowledge.entity.KnowledgeSource;
import com.dentalclinic.ai.knowledge.entity.KnowledgeSourceStatus;
import com.dentalclinic.ai.knowledge.entity.KnowledgeVectorDocument;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalCriteria;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalFilter;
import com.dentalclinic.ai.knowledge.repository.KnowledgeRetrievalRepository;
import com.dentalclinic.ai.knowledge.repository.KnowledgeSourceRepository;
import com.dentalclinic.ai.knowledge.repository.KnowledgeVectorDocumentRepository;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

@SpringBootTest
@ActiveProfiles("development")
class KnowledgeRetrievalPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();
    private static final String MODEL = "gemini-embedding-2";
    private static final String ACTIVE_HASH = "a".repeat(64);
    private static final String STAGING_HASH = "b".repeat(64);

    @Autowired
    private KnowledgeRetrievalRepository retrievalRepository;

    @Autowired
    private KnowledgeSourceRepository sourceRepository;

    @Autowired
    private KnowledgeVectorDocumentRepository vectorRepository;

    private final List<UUID> sourceIds = new ArrayList<>();
    private final List<UUID> vectorIds = new ArrayList<>();

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "knowledge-retrieval-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 15);
        registry.add("app.ai.enabled", () -> false);
    }

    @AfterEach
    void cleanUp() {
        vectorRepository.deleteAllById(vectorIds);
        sourceRepository.deleteAllById(sourceIds);
        vectorIds.clear();
        sourceIds.clear();
    }

    @Test
    void shouldUseCosineSimilarityTopKThresholdAndOnlyActiveVersion() {
        String sourceKey = "retrieval-active-" + UUID.randomUUID();
        saveSource(sourceKey, KnowledgeSourceStatus.FAILED);
        saveVector(sourceKey, ACTIVE_HASH, "EXACT", "DENTAL_CONDITION", vector(1.0f, 0.0f));
        saveVector(sourceKey, ACTIVE_HASH, "SECOND", "ORAL_CONDITION", vector(0.8f, 0.6f));
        saveVector(sourceKey, ACTIVE_HASH, "BELOW", "DENTAL_CONDITION", vector(0.0f, 1.0f));
        saveVector(sourceKey, STAGING_HASH, "STAGING", "DENTAL_CONDITION", vector(1.0f, 0.0f));

        var results = retrievalRepository.search(criteria(
                vector(1.0f, 0.0f),
                2,
                0.70,
                KnowledgeRetrievalFilter.none()
        ));

        assertThat(results).extracting(result -> metadataRecordId(result.metadataJson()))
                .containsExactly("EXACT", "SECOND");
        assertThat(results.get(0).score()).isCloseTo(1.0, offset(0.000001));
        assertThat(results.get(1).score()).isCloseTo(0.8, offset(0.000001));
    }

    @Test
    void shouldApplyMetadataFiltersWithoutReturningOtherSources() {
        String matchingSource = "retrieval-filter-" + UUID.randomUUID();
        String otherSource = "retrieval-other-" + UUID.randomUUID();
        saveSource(matchingSource, KnowledgeSourceStatus.SUCCESS);
        saveSource(otherSource, KnowledgeSourceStatus.SUCCESS);
        saveVector(matchingSource, ACTIVE_HASH, "MATCH", "DENTAL_CONDITION", vector(1.0f, 0.0f));
        saveVector(matchingSource, ACTIVE_HASH, "WRONG_CATEGORY", "ORAL_CONDITION", vector(1.0f, 0.0f));
        saveVector(otherSource, ACTIVE_HASH, "WRONG_SOURCE", "DENTAL_CONDITION", vector(1.0f, 0.0f));

        KnowledgeRetrievalFilter filter = new KnowledgeRetrievalFilter(
                matchingSource,
                "DENTAL_CONDITION",
                "vi",
                "PENDING_CLINICAL_REVIEW"
        );
        var results = retrievalRepository.search(criteria(vector(1.0f, 0.0f), 10, 0.5, filter));

        assertThat(results).hasSize(1);
        assertThat(metadataRecordId(results.getFirst().metadataJson())).isEqualTo("MATCH");
    }

    private KnowledgeRetrievalCriteria criteria(
            float[] embedding,
            int topK,
            double threshold,
            KnowledgeRetrievalFilter filter
    ) {
        return new KnowledgeRetrievalCriteria(
                embedding,
                MODEL,
                KnowledgeEmbeddingConstants.VECTOR_DIMENSION,
                topK,
                threshold,
                filter
        );
    }

    private void saveSource(String sourceKey, KnowledgeSourceStatus status) {
        KnowledgeSource source = new KnowledgeSource();
        source.setSourceKey(sourceKey);
        source.setFileName("nidcr_dental_knowledge_vi.json");
        source.setFileType(KnowledgeFileType.JSON);
        source.setContentHash(ACTIVE_HASH);
        source.setSourceVersion("1.0.0");
        source.setStatus(status);
        source.setChunkCount(3);
        source.setEmbeddingModel(MODEL);
        source.setEmbeddingDimension(KnowledgeEmbeddingConstants.VECTOR_DIMENSION);
        source.setClinicalReviewStatus("PENDING_CLINICAL_REVIEW");
        KnowledgeSource saved = sourceRepository.saveAndFlush(source);
        sourceIds.add(saved.getId());
    }

    private void saveVector(
            String sourceKey,
            String contentHash,
            String recordId,
            String category,
            float[] embedding
    ) {
        ObjectNode metadata = JsonNodeFactory.instance.objectNode()
                .put("sourceKey", sourceKey)
                .put("sourceVersion", "1.0.0")
                .put("recordId", recordId)
                .put("title", "Tiêu đề " + recordId)
                .put("category", category)
                .put("language", "vi")
                .put("sourceUrl", "https://example.test/" + recordId)
                .put("clinicalReviewStatus", "PENDING_CLINICAL_REVIEW")
                .put("embeddingModel", MODEL)
                .put("embeddingDimension", KnowledgeEmbeddingConstants.VECTOR_DIMENSION)
                .put("contentHash", contentHash)
                .put("syncId", UUID.randomUUID().toString());
        metadata.set("symptoms", textArray("đau răng"));
        metadata.set("serviceCategoryHints", textArray("khám tổng quát"));
        metadata.set("specializationHints", textArray("nha khoa tổng quát"));

        KnowledgeVectorDocument document = new KnowledgeVectorDocument();
        document.setContent("Nội dung " + recordId);
        document.setMetadata(metadata);
        document.setEmbedding(embedding);
        KnowledgeVectorDocument saved = vectorRepository.saveAndFlush(document);
        vectorIds.add(saved.getId());
    }

    private ArrayNode textArray(String value) {
        return JsonNodeFactory.instance.arrayNode().add(value);
    }

    private float[] vector(float first, float second) {
        float[] vector = new float[KnowledgeEmbeddingConstants.VECTOR_DIMENSION];
        vector[0] = first;
        vector[1] = second;
        return vector;
    }

    private String metadataRecordId(String metadataJson) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(metadataJson)
                    .path("recordId")
                    .asText();
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new AssertionError(exception);
        }
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }
}
