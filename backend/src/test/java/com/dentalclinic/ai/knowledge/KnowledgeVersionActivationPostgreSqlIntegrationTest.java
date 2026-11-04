package com.dentalclinic.ai.knowledge;

import com.dentalclinic.ai.knowledge.constant.KnowledgeEmbeddingConstants;
import com.dentalclinic.ai.knowledge.entity.KnowledgeFileType;
import com.dentalclinic.ai.knowledge.entity.KnowledgeSource;
import com.dentalclinic.ai.knowledge.entity.KnowledgeSourceStatus;
import com.dentalclinic.ai.knowledge.entity.KnowledgeVectorDocument;
import com.dentalclinic.ai.knowledge.exception.KnowledgeBaseException;
import com.dentalclinic.ai.knowledge.model.KnowledgeSyncActivation;
import com.dentalclinic.ai.knowledge.model.KnowledgeSyncClaim;
import com.dentalclinic.ai.knowledge.repository.KnowledgeSourceRepository;
import com.dentalclinic.ai.knowledge.repository.KnowledgeVectorDocumentRepository;
import com.dentalclinic.ai.knowledge.service.KnowledgeSyncStateService;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("development")
class KnowledgeVersionActivationPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();
    private static final String OLD_HASH = "a".repeat(64);
    private static final String NEW_HASH = "b".repeat(64);

    @Autowired
    private KnowledgeSourceRepository sourceRepository;

    @Autowired
    private KnowledgeVectorDocumentRepository vectorRepository;

    @Autowired
    private KnowledgeSyncStateService syncStateService;

    private final List<UUID> sourceIds = new ArrayList<>();
    private final List<UUID> vectorIds = new ArrayList<>();

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "knowledge-activation-integration-test");
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
    void shouldAtomicallyActivateVerifiedStagingVersionAndRemoveOldVersion() {
        String sourceKey = "activation-success-" + UUID.randomUUID();
        UUID syncToken = UUID.randomUUID();
        KnowledgeSource source = saveSource(sourceKey, syncToken);
        KnowledgeVectorDocument oldDocument = saveVector(
                sourceKey,
                OLD_HASH,
                UUID.randomUUID(),
                "OLD"
        );
        KnowledgeVectorDocument stagedDocument = saveVector(
                sourceKey,
                NEW_HASH,
                syncToken,
                "NEW"
        );

        syncStateService.activate(activation(sourceKey, syncToken, 1));

        KnowledgeSource reloaded = sourceRepository.findById(source.getId()).orElseThrow();
        assertThat(reloaded.getContentHash()).isEqualTo(NEW_HASH);
        assertThat(reloaded.getStatus()).isEqualTo(KnowledgeSourceStatus.SUCCESS);
        assertThat(reloaded.getSyncToken()).isNull();
        assertThat(vectorRepository.existsById(oldDocument.getId())).isFalse();
        assertThat(vectorRepository.existsById(stagedDocument.getId())).isTrue();
    }

    @Test
    void shouldKeepOldVersionWhenStagingVerificationFails() {
        String sourceKey = "activation-failure-" + UUID.randomUUID();
        UUID syncToken = UUID.randomUUID();
        KnowledgeSource source = saveSource(sourceKey, syncToken);
        KnowledgeVectorDocument oldDocument = saveVector(
                sourceKey,
                OLD_HASH,
                UUID.randomUUID(),
                "OLD"
        );
        KnowledgeVectorDocument stagedDocument = saveVector(
                sourceKey,
                NEW_HASH,
                syncToken,
                "NEW"
        );

        assertThatThrownBy(() -> syncStateService.activate(activation(sourceKey, syncToken, 2)))
                .isInstanceOf(KnowledgeBaseException.class)
                .hasMessageContaining("mong đợi 2 chunk");

        KnowledgeSource reloaded = sourceRepository.findById(source.getId()).orElseThrow();
        assertThat(reloaded.getContentHash()).isEqualTo(OLD_HASH);
        assertThat(reloaded.getSyncToken()).isEqualTo(syncToken);
        assertThat(vectorRepository.existsById(oldDocument.getId())).isTrue();
        assertThat(vectorRepository.existsById(stagedDocument.getId())).isTrue();
    }

    private KnowledgeSource saveSource(String sourceKey, UUID syncToken) {
        KnowledgeSource source = new KnowledgeSource();
        source.setSourceKey(sourceKey);
        source.setFileName("nidcr_dental_knowledge_vi.json");
        source.setFileType(KnowledgeFileType.JSON);
        source.setContentHash(OLD_HASH);
        source.setSourceVersion("1.0.0");
        source.setStatus(KnowledgeSourceStatus.SYNCING);
        source.setChunkCount(1);
        source.setEmbeddingModel("gemini-embedding-2");
        source.setEmbeddingDimension(KnowledgeEmbeddingConstants.VECTOR_DIMENSION);
        source.setClinicalReviewStatus("PENDING_CLINICAL_REVIEW");
        source.setSyncToken(syncToken);
        KnowledgeSource saved = sourceRepository.saveAndFlush(source);
        sourceIds.add(saved.getId());
        return saved;
    }

    private KnowledgeVectorDocument saveVector(
            String sourceKey,
            String contentHash,
            UUID syncId,
            String recordId
    ) {
        KnowledgeVectorDocument document = new KnowledgeVectorDocument();
        document.setContent("Nội dung " + recordId);
        document.setMetadata(JsonNodeFactory.instance.objectNode()
                .put("sourceKey", sourceKey)
                .put("sourceVersion", "1.0.0")
                .put("recordId", recordId)
                .put("contentHash", contentHash)
                .put("syncId", syncId.toString())
                .put("embeddingDimension", KnowledgeEmbeddingConstants.VECTOR_DIMENSION));
        float[] embedding = new float[KnowledgeEmbeddingConstants.VECTOR_DIMENSION];
        embedding[0] = 1.0f;
        document.setEmbedding(embedding);
        KnowledgeVectorDocument saved = vectorRepository.saveAndFlush(document);
        vectorIds.add(saved.getId());
        return saved;
    }

    private KnowledgeSyncActivation activation(
            String sourceKey,
            UUID syncToken,
            int chunkCount
    ) {
        return new KnowledgeSyncActivation(
                new KnowledgeSyncClaim(sourceKey, syncToken, OLD_HASH),
                NEW_HASH,
                "1.1.0",
                chunkCount,
                "gemini-embedding-2",
                KnowledgeEmbeddingConstants.VECTOR_DIMENSION,
                "PENDING_CLINICAL_REVIEW",
                Instant.parse("2026-10-29T01:30:00Z")
        );
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }
}
