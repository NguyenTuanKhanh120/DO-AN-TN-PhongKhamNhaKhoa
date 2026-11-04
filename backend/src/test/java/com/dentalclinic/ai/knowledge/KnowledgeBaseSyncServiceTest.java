package com.dentalclinic.ai.knowledge;

import com.dentalclinic.ai.knowledge.entity.KnowledgeFileType;
import com.dentalclinic.ai.knowledge.exception.KnowledgeBaseException;
import com.dentalclinic.ai.knowledge.model.DentalKnowledgeDataset;
import com.dentalclinic.ai.knowledge.model.EmbeddedKnowledgeChunk;
import com.dentalclinic.ai.knowledge.model.KnowledgeSourceFile;
import com.dentalclinic.ai.knowledge.model.KnowledgeSyncActivation;
import com.dentalclinic.ai.knowledge.model.KnowledgeSyncClaim;
import com.dentalclinic.ai.knowledge.model.KnowledgeSyncOutcome;
import com.dentalclinic.ai.knowledge.parser.NidcrDentalKnowledgeJsonParser;
import com.dentalclinic.ai.knowledge.service.KnowledgeBaseDiscoveryService;
import com.dentalclinic.ai.knowledge.service.KnowledgeBaseSyncService;
import com.dentalclinic.ai.knowledge.service.KnowledgeContentHashService;
import com.dentalclinic.ai.knowledge.service.KnowledgeEmbeddingService;
import com.dentalclinic.ai.knowledge.service.KnowledgeSyncStateService;
import com.dentalclinic.ai.knowledge.service.KnowledgeVectorStorageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeBaseSyncServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-29T01:30:00Z");
    private static final String NEW_HASH = "b".repeat(64);
    private static final String OLD_HASH = "a".repeat(64);

    private final KnowledgeBaseDiscoveryService discoveryService = mock(
            KnowledgeBaseDiscoveryService.class
    );
    private final KnowledgeContentHashService hashService = mock(KnowledgeContentHashService.class);
    private final NidcrDentalKnowledgeJsonParser parser = mock(NidcrDentalKnowledgeJsonParser.class);
    private final KnowledgeEmbeddingService embeddingService = mock(KnowledgeEmbeddingService.class);
    private final KnowledgeVectorStorageService vectorStorageService = mock(
            KnowledgeVectorStorageService.class
    );
    private final KnowledgeSyncStateService syncStateService = mock(KnowledgeSyncStateService.class);
    private final KnowledgeBaseSyncService service = new KnowledgeBaseSyncService(
            discoveryService,
            hashService,
            parser,
            embeddingService,
            vectorStorageService,
            syncStateService,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void shouldStageVerifyAndActivateChangedDataset() {
        KnowledgeSourceFile sourceFile = sourceFile();
        KnowledgeSyncClaim claim = claim(OLD_HASH);
        DentalKnowledgeDataset dataset = dataset();
        EmbeddedKnowledgeChunk embeddedChunk = embeddedChunk();
        when(discoveryService.discover()).thenReturn(List.of(sourceFile));
        when(hashService.sha256(sourceFile)).thenReturn(NEW_HASH, NEW_HASH);
        when(syncStateService.tryBeginSync(any(), any())).thenReturn(Optional.of(claim));
        when(parser.parse(sourceFile.path())).thenReturn(dataset);
        when(embeddingService.embed(dataset)).thenReturn(List.of(embeddedChunk));

        KnowledgeSyncOutcome outcome = service.synchronizePrimaryDataset();

        assertThat(outcome).isEqualTo(KnowledgeSyncOutcome.SYNCED);
        verify(vectorStorageService).storeStaging(
                List.of(embeddedChunk),
                NEW_HASH,
                claim.syncToken()
        );
        ArgumentCaptor<KnowledgeSyncActivation> activationCaptor = ArgumentCaptor.forClass(
                KnowledgeSyncActivation.class
        );
        verify(syncStateService).activate(activationCaptor.capture());
        KnowledgeSyncActivation activation = activationCaptor.getValue();
        assertThat(activation.contentHash()).isEqualTo(NEW_HASH);
        assertThat(activation.sourceVersion()).isEqualTo("1.0.0");
        assertThat(activation.chunkCount()).isEqualTo(1);
        assertThat(activation.embeddingModel()).isEqualTo("gemini-embedding-2");
        assertThat(activation.embeddingDimension()).isEqualTo(768);
        assertThat(activation.completedAt()).isEqualTo(NOW);
    }

    @Test
    void shouldSkipUnchangedFileBeforeParsingOrEmbedding() {
        KnowledgeSourceFile sourceFile = sourceFile();
        KnowledgeSyncClaim claim = claim(NEW_HASH);
        when(discoveryService.discover()).thenReturn(List.of(sourceFile));
        when(hashService.sha256(sourceFile)).thenReturn(NEW_HASH);
        when(syncStateService.tryBeginSync(any(), any())).thenReturn(Optional.of(claim));

        KnowledgeSyncOutcome outcome = service.synchronizePrimaryDataset();

        assertThat(outcome).isEqualTo(KnowledgeSyncOutcome.UNCHANGED);
        verify(syncStateService).markUnchanged(claim);
        verify(parser, never()).parse(any());
        verify(embeddingService, never()).embed(any());
    }

    @Test
    void shouldNotRunSecondSyncForSourceAlreadyOwnedByAnotherProcess() {
        KnowledgeSourceFile sourceFile = sourceFile();
        when(discoveryService.discover()).thenReturn(List.of(sourceFile));
        when(hashService.sha256(sourceFile)).thenReturn(NEW_HASH);
        when(syncStateService.tryBeginSync(any(), any())).thenReturn(Optional.empty());

        KnowledgeSyncOutcome outcome = service.synchronizePrimaryDataset();

        assertThat(outcome).isEqualTo(KnowledgeSyncOutcome.ALREADY_RUNNING);
        verify(parser, never()).parse(any());
        verify(embeddingService, never()).embed(any());
    }

    @Test
    void shouldFailWithoutStoringWhenFileChangesDuringParsing() {
        KnowledgeSourceFile sourceFile = sourceFile();
        KnowledgeSyncClaim claim = claim(OLD_HASH);
        DentalKnowledgeDataset dataset = dataset();
        when(discoveryService.discover()).thenReturn(List.of(sourceFile));
        when(hashService.sha256(sourceFile)).thenReturn(NEW_HASH, "c".repeat(64));
        when(syncStateService.tryBeginSync(any(), any())).thenReturn(Optional.of(claim));
        when(parser.parse(sourceFile.path())).thenReturn(dataset);

        assertThatThrownBy(service::synchronizePrimaryDataset)
                .isInstanceOf(KnowledgeBaseException.class)
                .hasMessageContaining("đã thay đổi");

        verify(embeddingService, never()).embed(any());
        verify(vectorStorageService, never()).storeStaging(any(), any(), any());
        verify(vectorStorageService).deleteStaging(claim.sourceKey(), claim.syncToken());
        verify(syncStateService).markFailed(claim, "Dataset đã thay đổi trong khi đang đồng bộ");
    }

    @Test
    void shouldNotWritePartialVersionWhenParserRejectsDataset() {
        KnowledgeSourceFile sourceFile = sourceFile();
        KnowledgeSyncClaim claim = claim(OLD_HASH);
        KnowledgeBaseException parseFailure = new KnowledgeBaseException("invalid record 7");
        when(discoveryService.discover()).thenReturn(List.of(sourceFile));
        when(hashService.sha256(sourceFile)).thenReturn(NEW_HASH);
        when(syncStateService.tryBeginSync(any(), any())).thenReturn(Optional.of(claim));
        when(parser.parse(sourceFile.path())).thenThrow(parseFailure);

        assertThatThrownBy(service::synchronizePrimaryDataset).isSameAs(parseFailure);

        verify(embeddingService, never()).embed(any());
        verify(vectorStorageService, never()).storeStaging(any(), any(), any());
        verify(vectorStorageService).deleteStaging(claim.sourceKey(), claim.syncToken());
        verify(syncStateService).markFailed(claim, "invalid record 7");
    }

    @Test
    void shouldCleanStagingAndPreserveActivePointerWhenActivationFails() {
        KnowledgeSourceFile sourceFile = sourceFile();
        KnowledgeSyncClaim claim = claim(OLD_HASH);
        DentalKnowledgeDataset dataset = dataset();
        EmbeddedKnowledgeChunk embeddedChunk = embeddedChunk();
        when(discoveryService.discover()).thenReturn(List.of(sourceFile));
        when(hashService.sha256(sourceFile)).thenReturn(NEW_HASH, NEW_HASH);
        when(syncStateService.tryBeginSync(any(), any())).thenReturn(Optional.of(claim));
        when(parser.parse(sourceFile.path())).thenReturn(dataset);
        when(embeddingService.embed(dataset)).thenReturn(List.of(embeddedChunk));
        KnowledgeBaseException activationFailure = new KnowledgeBaseException("activation failed");
        org.mockito.Mockito.doThrow(activationFailure).when(syncStateService).activate(any());

        assertThatThrownBy(service::synchronizePrimaryDataset).isSameAs(activationFailure);

        verify(vectorStorageService).deleteStaging(claim.sourceKey(), claim.syncToken());
        verify(syncStateService).markFailed(claim, "activation failed");
    }

    private KnowledgeSourceFile sourceFile() {
        return new KnowledgeSourceFile(
                Path.of("knowledge-base/nidcr_dental_knowledge_vi.json"),
                NidcrDentalKnowledgeJsonParser.PRIMARY_DATASET_FILE_NAME,
                KnowledgeFileType.JSON,
                1024,
                NOW
        );
    }

    private KnowledgeSyncClaim claim(String previousHash) {
        return new KnowledgeSyncClaim(
                "nidcr-dental-knowledge-vi",
                UUID.randomUUID(),
                previousHash
        );
    }

    private DentalKnowledgeDataset dataset() {
        DentalKnowledgeDataset dataset = mock(DentalKnowledgeDataset.class);
        when(dataset.version()).thenReturn("1.0.0");
        when(dataset.clinicalReviewStatus()).thenReturn("PENDING_CLINICAL_REVIEW");
        return dataset;
    }

    private EmbeddedKnowledgeChunk embeddedChunk() {
        EmbeddedKnowledgeChunk chunk = mock(EmbeddedKnowledgeChunk.class);
        when(chunk.embeddingModel()).thenReturn("gemini-embedding-2");
        when(chunk.embeddingDimension()).thenReturn(768);
        return chunk;
    }
}
