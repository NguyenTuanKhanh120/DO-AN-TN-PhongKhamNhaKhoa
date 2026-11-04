package com.dentalclinic.ai.knowledge;

import com.dentalclinic.ai.knowledge.entity.KnowledgeFileType;
import com.dentalclinic.ai.knowledge.entity.KnowledgeSource;
import com.dentalclinic.ai.knowledge.entity.KnowledgeSourceStatus;
import com.dentalclinic.ai.knowledge.exception.KnowledgeBaseException;
import com.dentalclinic.ai.knowledge.model.KnowledgeSourceFile;
import com.dentalclinic.ai.knowledge.model.KnowledgeSyncActivation;
import com.dentalclinic.ai.knowledge.model.KnowledgeSyncClaim;
import com.dentalclinic.ai.knowledge.repository.KnowledgeSourceRepository;
import com.dentalclinic.ai.knowledge.repository.KnowledgeVectorDocumentRepository;
import com.dentalclinic.ai.knowledge.service.KnowledgeSyncStateService;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeSyncStateServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-29T01:00:00Z");
    private static final String SOURCE_KEY = "nidcr-dental-knowledge-vi";

    private final KnowledgeSourceRepository sourceRepository = mock(KnowledgeSourceRepository.class);
    private final KnowledgeVectorDocumentRepository vectorRepository = mock(
            KnowledgeVectorDocumentRepository.class
    );
    private final KnowledgeSyncStateService service = new KnowledgeSyncStateService(
            sourceRepository,
            vectorRepository,
            Clock.fixed(NOW, ZoneOffset.UTC),
            Duration.ofMinutes(30)
    );

    @Test
    void shouldAtomicallyClaimSourceAndRetainPreviousSuccessfulHash() {
        KnowledgeSource source = syncingSource();
        source.setContentHash("a".repeat(64));
        when(sourceRepository.tryBeginSync(
                eq(SOURCE_KEY),
                eq("nidcr_dental_knowledge_vi.json"),
                eq("JSON"),
                eq(NOW),
                eq(NOW.minus(Duration.ofMinutes(30))),
                any(UUID.class)
        )).thenAnswer(invocation -> {
            source.setSyncToken(invocation.getArgument(5));
            return 1;
        });
        when(sourceRepository.findBySourceKey(SOURCE_KEY)).thenReturn(Optional.of(source));

        Optional<KnowledgeSyncClaim> claim = service.tryBeginSync(SOURCE_KEY, sourceFile());

        assertThat(claim).isPresent();
        assertThat(claim.orElseThrow().previousContentHash()).isEqualTo("a".repeat(64));
        assertThat(claim.orElseThrow().syncToken()).isEqualTo(source.getSyncToken());
    }

    @Test
    void shouldRefuseClaimWhileAnotherFreshSyncOwnsSource() {
        when(sourceRepository.tryBeginSync(
                eq(SOURCE_KEY),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(0);

        assertThat(service.tryBeginSync(SOURCE_KEY, sourceFile())).isEmpty();

        verify(sourceRepository, never()).findBySourceKey(any());
    }

    @Test
    void shouldActivateOnlyAfterAllStagingVectorsPassValidation() {
        KnowledgeSource source = syncingSource();
        KnowledgeSyncClaim claim = claim(source);
        when(vectorRepository.countValidStagingDocuments(
                SOURCE_KEY,
                "b".repeat(64),
                claim.syncToken().toString(),
                768
        )).thenReturn(16L);
        when(sourceRepository.findBySourceKeyForUpdate(SOURCE_KEY)).thenReturn(Optional.of(source));

        service.activate(activation(claim, 16));

        assertThat(source.getContentHash()).isEqualTo("b".repeat(64));
        assertThat(source.getSourceVersion()).isEqualTo("1.1.0");
        assertThat(source.getChunkCount()).isEqualTo(16);
        assertThat(source.getEmbeddingModel()).isEqualTo("gemini-embedding-2");
        assertThat(source.getEmbeddingDimension()).isEqualTo(768);
        assertThat(source.getStatus()).isEqualTo(KnowledgeSourceStatus.SUCCESS);
        assertThat(source.getLastSuccessAt()).isEqualTo(NOW);
        assertThat(source.getSyncToken()).isNull();
        verify(vectorRepository).deleteOtherVersions(
                SOURCE_KEY,
                claim.syncToken().toString()
        );
    }

    @Test
    void shouldKeepOldVersionWhenStagingVectorCountIsIncomplete() {
        KnowledgeSource source = syncingSource();
        source.setContentHash("a".repeat(64));
        KnowledgeSyncClaim claim = claim(source);
        when(vectorRepository.countValidStagingDocuments(
                SOURCE_KEY,
                "b".repeat(64),
                claim.syncToken().toString(),
                768
        )).thenReturn(15L);

        assertThatThrownBy(() -> service.activate(activation(claim, 16)))
                .isInstanceOf(KnowledgeBaseException.class)
                .hasMessageContaining("mong đợi 16 chunk");

        assertThat(source.getContentHash()).isEqualTo("a".repeat(64));
        verify(sourceRepository, never()).findBySourceKeyForUpdate(any());
        verify(vectorRepository, never()).deleteOtherVersions(any(), any());
    }

    @Test
    void shouldRecordSanitizedFailureOnlyForOwningSyncToken() {
        KnowledgeSource source = syncingSource();
        KnowledgeSyncClaim claim = claim(source);
        when(sourceRepository.findBySourceKeyForUpdate(SOURCE_KEY)).thenReturn(Optional.of(source));

        service.markFailed(claim, "Embedding failed\r\nwithout exposing a stack trace");

        assertThat(source.getStatus()).isEqualTo(KnowledgeSourceStatus.FAILED);
        assertThat(source.getLastError())
                .isEqualTo("Embedding failed without exposing a stack trace");
        assertThat(source.getSyncToken()).isNull();
    }

    private KnowledgeSourceFile sourceFile() {
        return new KnowledgeSourceFile(
                Path.of("nidcr_dental_knowledge_vi.json"),
                "nidcr_dental_knowledge_vi.json",
                KnowledgeFileType.JSON,
                100,
                NOW
        );
    }

    private KnowledgeSource syncingSource() {
        KnowledgeSource source = new KnowledgeSource();
        source.setSourceKey(SOURCE_KEY);
        source.setStatus(KnowledgeSourceStatus.SYNCING);
        return source;
    }

    private KnowledgeSyncClaim claim(KnowledgeSource source) {
        UUID token = UUID.randomUUID();
        source.setSyncToken(token);
        return new KnowledgeSyncClaim(SOURCE_KEY, token, source.getContentHash());
    }

    private KnowledgeSyncActivation activation(KnowledgeSyncClaim claim, int chunkCount) {
        return new KnowledgeSyncActivation(
                claim,
                "b".repeat(64),
                "1.1.0",
                chunkCount,
                "gemini-embedding-2",
                768,
                "PENDING_CLINICAL_REVIEW",
                NOW
        );
    }
}
