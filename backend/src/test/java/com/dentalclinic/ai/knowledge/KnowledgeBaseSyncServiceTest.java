package com.dentalclinic.ai.knowledge;

import com.dentalclinic.ai.knowledge.entity.KnowledgeFileType;
import com.dentalclinic.ai.knowledge.exception.KnowledgeBaseException;
import com.dentalclinic.ai.knowledge.model.DentalKnowledgeDataset;
import com.dentalclinic.ai.knowledge.model.EmbeddedKnowledgeChunk;
import com.dentalclinic.ai.knowledge.model.KnowledgeChunk;
import com.dentalclinic.ai.knowledge.model.KnowledgeChunkMetadata;
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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
            Clock.fixed(NOW, ZoneOffset.UTC),
            30,
            Duration.ZERO
    );

    @Test
    void shouldStageVerifyAndActivateChangedDataset() {
        KnowledgeSourceFile sourceFile = sourceFile();
        KnowledgeSyncClaim claim = claim(OLD_HASH);
        DentalKnowledgeDataset dataset = dataset();
        KnowledgeChunk chunk = knowledgeChunk("NIDCR-VI-001");
        EmbeddedKnowledgeChunk embeddedChunk = embeddedChunk();
        when(discoveryService.discover()).thenReturn(List.of(sourceFile));
        when(hashService.sha256(sourceFile)).thenReturn(NEW_HASH, NEW_HASH);
        when(syncStateService.tryBeginSync(any(), any(), any())).thenReturn(Optional.of(claim));
        when(parser.parse(sourceFile.path())).thenReturn(dataset);
        stubEmbedding(dataset, List.of(chunk), List.of(embeddedChunk));
        when(vectorStorageService.findStoredRecordIds(
                claim.sourceKey(), NEW_HASH, "gemini-embedding-2", 768
        )).thenReturn(Set.of());

        KnowledgeSyncOutcome outcome = service.synchronizeAllDatasets();

        assertThat(outcome).isEqualTo(KnowledgeSyncOutcome.SYNCED);
        verify(vectorStorageService).storeStaging(
                List.of(embeddedChunk),
                NEW_HASH,
                claim.syncToken()
        );
        verify(syncStateService).recordProgress(claim, NEW_HASH, 0, 1);
        verify(syncStateService).recordProgress(claim, NEW_HASH, 1, 1);
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
        when(syncStateService.tryBeginSync(any(), any(), any())).thenReturn(Optional.of(claim));

        KnowledgeSyncOutcome outcome = service.synchronizeAllDatasets();

        assertThat(outcome).isEqualTo(KnowledgeSyncOutcome.UNCHANGED);
        verify(syncStateService).markUnchanged(claim);
        verify(parser, never()).parse(any());
        verify(embeddingService, never()).chunk(any());
    }

    @Test
    void shouldNotRunSecondSyncForSourceAlreadyOwnedByAnotherProcess() {
        KnowledgeSourceFile sourceFile = sourceFile();
        when(discoveryService.discover()).thenReturn(List.of(sourceFile));
        when(hashService.sha256(sourceFile)).thenReturn(NEW_HASH);
        when(syncStateService.tryBeginSync(any(), any(), any())).thenReturn(Optional.empty());

        KnowledgeSyncOutcome outcome = service.synchronizeAllDatasets();

        assertThat(outcome).isEqualTo(KnowledgeSyncOutcome.ALREADY_RUNNING);
        verify(parser, never()).parse(any());
        verify(embeddingService, never()).chunk(any());
    }

    @Test
    void shouldFailWithoutStoringWhenFileChangesDuringParsing() {
        KnowledgeSourceFile sourceFile = sourceFile();
        KnowledgeSyncClaim claim = claim(OLD_HASH);
        DentalKnowledgeDataset dataset = dataset();
        when(discoveryService.discover()).thenReturn(List.of(sourceFile));
        when(hashService.sha256(sourceFile)).thenReturn(NEW_HASH, "c".repeat(64));
        when(syncStateService.tryBeginSync(any(), any(), any())).thenReturn(Optional.of(claim));
        when(parser.parse(sourceFile.path())).thenReturn(dataset);

        assertThatThrownBy(service::synchronizeAllDatasets)
                .isInstanceOf(KnowledgeBaseException.class)
                .hasMessageContaining("đã thay đổi");

        verify(embeddingService, never()).chunk(any());
        verify(vectorStorageService, never()).storeStaging(any(), any(), any());
        verify(syncStateService).markFailed(claim, "Dataset đã thay đổi trong khi đang đồng bộ");
    }

    @Test
    void shouldNotWritePartialVersionWhenParserRejectsDataset() {
        KnowledgeSourceFile sourceFile = sourceFile();
        KnowledgeSyncClaim claim = claim(OLD_HASH);
        KnowledgeBaseException parseFailure = new KnowledgeBaseException("invalid record 7");
        when(discoveryService.discover()).thenReturn(List.of(sourceFile));
        when(hashService.sha256(sourceFile)).thenReturn(NEW_HASH);
        when(syncStateService.tryBeginSync(any(), any(), any())).thenReturn(Optional.of(claim));
        when(parser.parse(sourceFile.path())).thenThrow(parseFailure);

        assertThatThrownBy(service::synchronizeAllDatasets).isSameAs(parseFailure);

        verify(embeddingService, never()).chunk(any());
        verify(vectorStorageService, never()).storeStaging(any(), any(), any());
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
        when(syncStateService.tryBeginSync(any(), any(), any())).thenReturn(Optional.of(claim));
        when(parser.parse(sourceFile.path())).thenReturn(dataset);
        KnowledgeChunk chunk = knowledgeChunk("NIDCR-VI-001");
        stubEmbedding(dataset, List.of(chunk), List.of(embeddedChunk));
        when(vectorStorageService.findStoredRecordIds(
                claim.sourceKey(), NEW_HASH, "gemini-embedding-2", 768
        )).thenReturn(Set.of());
        KnowledgeBaseException activationFailure = new KnowledgeBaseException("activation failed");
        org.mockito.Mockito.doThrow(activationFailure).when(syncStateService).activate(any());

        assertThatThrownBy(service::synchronizeAllDatasets).isSameAs(activationFailure);

        verify(syncStateService).markFailed(claim, "activation failed");
    }

    @Test
    void shouldKeepCompletedBatchWhenLaterBatchHitsDailyQuota() {
        KnowledgeSourceFile sourceFile = sourceFile();
        KnowledgeSyncClaim claim = claim(OLD_HASH);
        DentalKnowledgeDataset dataset = dataset();
        KnowledgeChunk first = knowledgeChunk("NIDCR-VI-001");
        KnowledgeChunk second = knowledgeChunk("NIDCR-VI-002");
        EmbeddedKnowledgeChunk embeddedFirst = embeddedChunk();
        KnowledgeBaseException quotaFailure = new KnowledgeBaseException(
                "Gemini embedding đã hết hạn mức theo ngày"
        );
        when(discoveryService.discover()).thenReturn(List.of(sourceFile));
        when(hashService.sha256(sourceFile)).thenReturn(NEW_HASH, NEW_HASH);
        when(syncStateService.tryBeginSync(any(), any(), any())).thenReturn(Optional.of(claim));
        when(parser.parse(sourceFile.path())).thenReturn(dataset);
        when(embeddingService.chunk(dataset)).thenReturn(List.of(first, second));
        when(embeddingService.embeddingModel()).thenReturn("gemini-embedding-2");
        when(embeddingService.embeddingDimension()).thenReturn(768);
        when(vectorStorageService.findStoredRecordIds(
                claim.sourceKey(), NEW_HASH, "gemini-embedding-2", 768
        )).thenReturn(Set.of());
        when(embeddingService.embedChunks(List.of(first))).thenReturn(List.of(embeddedFirst));
        when(embeddingService.embedChunks(List.of(second))).thenThrow(quotaFailure);

        assertThatThrownBy(() -> service(1).synchronizeAllDatasets()).isSameAs(quotaFailure);

        verify(vectorStorageService).storeStaging(
                List.of(embeddedFirst),
                NEW_HASH,
                claim.syncToken()
        );
        verify(syncStateService).recordProgress(claim, NEW_HASH, 0, 2);
        verify(syncStateService).recordProgress(claim, NEW_HASH, 1, 2);
        verify(syncStateService).markFailed(claim, quotaFailure.getMessage());
        verify(syncStateService, never()).activate(any());
    }

    @Test
    void shouldResumeWithOnlyMissingChunksFromPreviousAttempt() {
        KnowledgeSourceFile sourceFile = sourceFile();
        KnowledgeSyncClaim claim = claim(OLD_HASH);
        DentalKnowledgeDataset dataset = dataset();
        KnowledgeChunk completed = knowledgeChunk("NIDCR-VI-001");
        KnowledgeChunk missing = knowledgeChunk("NIDCR-VI-002");
        EmbeddedKnowledgeChunk embeddedMissing = embeddedChunk();
        when(discoveryService.discover()).thenReturn(List.of(sourceFile));
        when(hashService.sha256(sourceFile)).thenReturn(NEW_HASH, NEW_HASH);
        when(syncStateService.tryBeginSync(any(), any(), any())).thenReturn(Optional.of(claim));
        when(parser.parse(sourceFile.path())).thenReturn(dataset);
        when(embeddingService.chunk(dataset)).thenReturn(List.of(completed, missing));
        when(embeddingService.embeddingModel()).thenReturn("gemini-embedding-2");
        when(embeddingService.embeddingDimension()).thenReturn(768);
        when(vectorStorageService.findStoredRecordIds(
                claim.sourceKey(), NEW_HASH, "gemini-embedding-2", 768
        )).thenReturn(Set.of("NIDCR-VI-001"));
        when(embeddingService.embedChunks(List.of(missing)))
                .thenReturn(List.of(embeddedMissing));

        KnowledgeSyncOutcome outcome = service(1).synchronizeAllDatasets();

        assertThat(outcome).isEqualTo(KnowledgeSyncOutcome.SYNCED);
        verify(embeddingService, never()).embedChunks(List.of(completed));
        verify(vectorStorageService).storeStaging(
                List.of(embeddedMissing),
                NEW_HASH,
                claim.syncToken()
        );
        verify(syncStateService).recordProgress(claim, NEW_HASH, 1, 2);
        verify(syncStateService).recordProgress(claim, NEW_HASH, 2, 2);
        ArgumentCaptor<KnowledgeSyncActivation> activationCaptor = ArgumentCaptor.forClass(
                KnowledgeSyncActivation.class
        );
        verify(syncStateService).activate(activationCaptor.capture());
        assertThat(activationCaptor.getValue().chunkCount()).isEqualTo(2);
    }

    @Test
    void shouldSynchronizeEveryManagedDatasetAsAnIndependentSource() {
        KnowledgeSourceFile nidcrSource = sourceFile();
        KnowledgeSourceFile qaSource = managedSourceFile(
                NidcrDentalKnowledgeJsonParser.VIETNAMESE_DENTAL_QA_FILE_NAME,
                "vietnamese-dental-qa-rag"
        );
        String qaHash = "d".repeat(64);
        KnowledgeSyncClaim nidcrClaim = claim(OLD_HASH);
        KnowledgeSyncClaim qaClaim = new KnowledgeSyncClaim(
                "vietnamese-dental-qa-rag",
                UUID.randomUUID(),
                OLD_HASH
        );
        DentalKnowledgeDataset nidcrDataset = dataset();
        DentalKnowledgeDataset qaDataset = dataset("vietnamese-dental-qa-rag");
        EmbeddedKnowledgeChunk nidcrChunk = embeddedChunk();
        EmbeddedKnowledgeChunk qaChunk = embeddedChunk();
        when(discoveryService.discover()).thenReturn(List.of(nidcrSource, qaSource));
        when(hashService.sha256(nidcrSource)).thenReturn(NEW_HASH, NEW_HASH);
        when(hashService.sha256(qaSource)).thenReturn(qaHash, qaHash);
        when(syncStateService.tryBeginSync("nidcr-dental-knowledge-vi", nidcrSource, NEW_HASH))
                .thenReturn(Optional.of(nidcrClaim));
        when(syncStateService.tryBeginSync("vietnamese-dental-qa-rag", qaSource, qaHash))
                .thenReturn(Optional.of(qaClaim));
        when(parser.parse(nidcrSource.path())).thenReturn(nidcrDataset);
        when(parser.parse(qaSource.path())).thenReturn(qaDataset);
        KnowledgeChunk nidcrKnowledgeChunk = knowledgeChunk("NIDCR-VI-001");
        KnowledgeChunk qaKnowledgeChunk = knowledgeChunk("DENTAL-QA-001");
        when(embeddingService.chunk(nidcrDataset)).thenReturn(List.of(nidcrKnowledgeChunk));
        when(embeddingService.chunk(qaDataset)).thenReturn(List.of(qaKnowledgeChunk));
        when(embeddingService.embeddingModel()).thenReturn("gemini-embedding-2");
        when(embeddingService.embeddingDimension()).thenReturn(768);
        when(embeddingService.embedChunks(List.of(nidcrKnowledgeChunk)))
                .thenReturn(List.of(nidcrChunk));
        when(embeddingService.embedChunks(List.of(qaKnowledgeChunk)))
                .thenReturn(List.of(qaChunk));
        when(vectorStorageService.findStoredRecordIds(
                "nidcr-dental-knowledge-vi", NEW_HASH, "gemini-embedding-2", 768
        )).thenReturn(Set.of());
        when(vectorStorageService.findStoredRecordIds(
                "vietnamese-dental-qa-rag", qaHash, "gemini-embedding-2", 768
        )).thenReturn(Set.of());

        KnowledgeSyncOutcome outcome = service.synchronizeAllDatasets();

        assertThat(outcome).isEqualTo(KnowledgeSyncOutcome.SYNCED);
        verify(vectorStorageService).storeStaging(
                List.of(nidcrChunk),
                NEW_HASH,
                nidcrClaim.syncToken()
        );
        verify(vectorStorageService).storeStaging(
                List.of(qaChunk),
                qaHash,
                qaClaim.syncToken()
        );
        verify(syncStateService, org.mockito.Mockito.times(2)).activate(any());
    }

    private KnowledgeSourceFile sourceFile() {
        return managedSourceFile(
                NidcrDentalKnowledgeJsonParser.PRIMARY_DATASET_FILE_NAME,
                "nidcr-dental-knowledge-vi"
        );
    }

    private KnowledgeBaseSyncService service(int batchSize) {
        return new KnowledgeBaseSyncService(
                discoveryService,
                hashService,
                parser,
                embeddingService,
                vectorStorageService,
                syncStateService,
                Clock.fixed(NOW, ZoneOffset.UTC),
                batchSize,
                Duration.ZERO
        );
    }

    private KnowledgeSourceFile managedSourceFile(String fileName, String sourceKey) {
        KnowledgeSourceFile sourceFile = new KnowledgeSourceFile(
                Path.of("knowledge-base").resolve(fileName),
                fileName,
                KnowledgeFileType.JSON,
                1024,
                NOW
        );
        when(parser.managedSourceKey(sourceFile))
                .thenReturn(Optional.of(sourceKey));
        return sourceFile;
    }

    private KnowledgeSyncClaim claim(String previousHash) {
        return new KnowledgeSyncClaim(
                "nidcr-dental-knowledge-vi",
                UUID.randomUUID(),
                previousHash
        );
    }

    private DentalKnowledgeDataset dataset() {
        return dataset("nidcr-dental-knowledge-vi");
    }

    private DentalKnowledgeDataset dataset(String datasetId) {
        DentalKnowledgeDataset dataset = mock(DentalKnowledgeDataset.class);
        when(dataset.datasetId()).thenReturn(datasetId);
        when(dataset.version()).thenReturn("1.0.0");
        when(dataset.clinicalReviewStatus()).thenReturn("PENDING_CLINICAL_REVIEW");
        return dataset;
    }

    private void stubEmbedding(
            DentalKnowledgeDataset dataset,
            List<KnowledgeChunk> chunks,
            List<EmbeddedKnowledgeChunk> embeddedChunks
    ) {
        when(embeddingService.chunk(dataset)).thenReturn(chunks);
        when(embeddingService.embeddingModel()).thenReturn("gemini-embedding-2");
        when(embeddingService.embeddingDimension()).thenReturn(768);
        when(embeddingService.embedChunks(chunks)).thenReturn(embeddedChunks);
    }

    private KnowledgeChunk knowledgeChunk(String recordId) {
        return new KnowledgeChunk(
                "Nội dung " + recordId,
                new KnowledgeChunkMetadata(
                        "nidcr-dental-knowledge-vi",
                        "1.0.0",
                        recordId,
                        "Tiêu đề",
                        "general",
                        "vi",
                        List.of(),
                        List.of(),
                        List.of(),
                        "https://example.com/" + recordId,
                        "PENDING_CLINICAL_REVIEW"
                )
        );
    }

    private EmbeddedKnowledgeChunk embeddedChunk() {
        EmbeddedKnowledgeChunk chunk = mock(EmbeddedKnowledgeChunk.class);
        when(chunk.embeddingModel()).thenReturn("gemini-embedding-2");
        when(chunk.embeddingDimension()).thenReturn(768);
        return chunk;
    }
}
