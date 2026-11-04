package com.dentalclinic.ai.knowledge;

import com.dentalclinic.ai.knowledge.entity.KnowledgeFileType;
import com.dentalclinic.ai.knowledge.entity.KnowledgeSource;
import com.dentalclinic.ai.knowledge.entity.KnowledgeSourceStatus;
import com.dentalclinic.ai.knowledge.model.KnowledgeSourceFile;
import com.dentalclinic.ai.knowledge.repository.KnowledgeSourceRepository;
import com.dentalclinic.ai.knowledge.service.KnowledgeSyncStateService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.crypto.SecretKey;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("development")
class KnowledgeSourcePostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();

    @Autowired
    private KnowledgeSourceRepository knowledgeSourceRepository;

    @Autowired
    private KnowledgeSyncStateService knowledgeSyncStateService;

    private final List<UUID> createdSourceIds = new ArrayList<>();

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "knowledge-source-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 15);
        registry.add("app.ai.enabled", () -> false);
    }

    @AfterEach
    void cleanUp() {
        knowledgeSourceRepository.deleteAllById(createdSourceIds);
        createdSourceIds.clear();
    }

    @Test
    void shouldPersistPendingKnowledgeSourceMetadata() {
        KnowledgeSource source = source("pending-source-" + UUID.randomUUID());

        KnowledgeSource saved = knowledgeSourceRepository.saveAndFlush(source);
        createdSourceIds.add(saved.getId());

        KnowledgeSource reloaded = knowledgeSourceRepository.findBySourceKey(source.getSourceKey())
                .orElseThrow();
        assertThat(reloaded.getFileName()).isEqualTo("nidcr_dental_knowledge_vi.json");
        assertThat(reloaded.getFileType()).isEqualTo(KnowledgeFileType.JSON);
        assertThat(reloaded.getStatus()).isEqualTo(KnowledgeSourceStatus.PENDING);
        assertThat(reloaded.getChunkCount()).isZero();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldRejectDuplicateSourceKey() {
        String sourceKey = "duplicate-source-" + UUID.randomUUID();
        KnowledgeSource first = knowledgeSourceRepository.saveAndFlush(source(sourceKey));
        createdSourceIds.add(first.getId());

        assertThatThrownBy(() -> knowledgeSourceRepository.saveAndFlush(source(sourceKey)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_knowledge_sources_key");
    }

    @Test
    void shouldPersistSyncTokenUsedForConcurrencyOwnership() {
        UUID syncToken = UUID.randomUUID();
        KnowledgeSource source = source("sync-token-source-" + UUID.randomUUID());
        source.setStatus(KnowledgeSourceStatus.SYNCING);
        source.setSyncToken(syncToken);

        KnowledgeSource saved = knowledgeSourceRepository.saveAndFlush(source);
        createdSourceIds.add(saved.getId());

        KnowledgeSource reloaded = knowledgeSourceRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getSyncToken()).isEqualTo(syncToken);
        assertThat(reloaded.getStatus()).isEqualTo(KnowledgeSourceStatus.SYNCING);
    }

    @Test
    void shouldAllowOnlyOneFreshSyncClaimForSameSource() {
        String sourceKey = "concurrent-source-" + UUID.randomUUID();
        KnowledgeSourceFile sourceFile = new KnowledgeSourceFile(
                Path.of("nidcr_dental_knowledge_vi.json"),
                "nidcr_dental_knowledge_vi.json",
                KnowledgeFileType.JSON,
                100,
                Instant.now()
        );

        var firstClaim = knowledgeSyncStateService.tryBeginSync(sourceKey, sourceFile);
        KnowledgeSource source = knowledgeSourceRepository.findBySourceKey(sourceKey).orElseThrow();
        createdSourceIds.add(source.getId());
        var secondClaim = knowledgeSyncStateService.tryBeginSync(sourceKey, sourceFile);

        assertThat(firstClaim).isPresent();
        assertThat(secondClaim).isEmpty();
        assertThat(source.getSyncToken()).isEqualTo(firstClaim.orElseThrow().syncToken());
    }

    private KnowledgeSource source(String sourceKey) {
        KnowledgeSource source = new KnowledgeSource();
        source.setSourceKey(sourceKey);
        source.setFileName("nidcr_dental_knowledge_vi.json");
        source.setFileType(KnowledgeFileType.JSON);
        source.setSourceVersion("1.0.0");
        source.setClinicalReviewStatus("PENDING_CLINICAL_REVIEW");
        return source;
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }
}
