package com.dentalclinic.ai.knowledge.service;

import com.dentalclinic.ai.knowledge.exception.KnowledgeBaseException;
import com.dentalclinic.ai.knowledge.model.DentalKnowledgeDataset;
import com.dentalclinic.ai.knowledge.model.EmbeddedKnowledgeChunk;
import com.dentalclinic.ai.knowledge.model.KnowledgeChunk;
import com.dentalclinic.ai.knowledge.model.KnowledgeSourceFile;
import com.dentalclinic.ai.knowledge.model.KnowledgeSyncActivation;
import com.dentalclinic.ai.knowledge.model.KnowledgeSyncClaim;
import com.dentalclinic.ai.knowledge.model.KnowledgeSyncOutcome;
import com.dentalclinic.ai.knowledge.parser.NidcrDentalKnowledgeJsonParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class KnowledgeBaseSyncService {

    private static final int MAX_EMBEDDING_BATCH_SIZE = 30;

    private final KnowledgeBaseDiscoveryService discoveryService;
    private final KnowledgeContentHashService hashService;
    private final NidcrDentalKnowledgeJsonParser parser;
    private final KnowledgeEmbeddingService embeddingService;
    private final KnowledgeVectorStorageService vectorStorageService;
    private final KnowledgeSyncStateService syncStateService;
    private final Clock clock;
    private final int embeddingBatchSize;
    private final Duration embeddingBatchDelay;

    public KnowledgeBaseSyncService(
            KnowledgeBaseDiscoveryService discoveryService,
            KnowledgeContentHashService hashService,
            NidcrDentalKnowledgeJsonParser parser,
            KnowledgeEmbeddingService embeddingService,
            KnowledgeVectorStorageService vectorStorageService,
            KnowledgeSyncStateService syncStateService,
            Clock clock,
            @Value("${app.ai.knowledge-base.sync-batch-size:30}") int embeddingBatchSize,
            @Value("${app.ai.knowledge-base.sync-batch-delay:21s}") Duration embeddingBatchDelay
    ) {
        if (embeddingBatchSize <= 0 || embeddingBatchSize > MAX_EMBEDDING_BATCH_SIZE) {
            throw new IllegalArgumentException("Knowledge sync batch size must be between 1 and 30");
        }
        if (embeddingBatchDelay == null || embeddingBatchDelay.isNegative()) {
            throw new IllegalArgumentException("Knowledge sync batch delay must not be negative");
        }
        this.discoveryService = discoveryService;
        this.hashService = hashService;
        this.parser = parser;
        this.embeddingService = embeddingService;
        this.vectorStorageService = vectorStorageService;
        this.syncStateService = syncStateService;
        this.clock = clock;
        this.embeddingBatchSize = embeddingBatchSize;
        this.embeddingBatchDelay = embeddingBatchDelay;
    }

    public KnowledgeSyncOutcome synchronizeAllDatasets() {
        List<ManagedKnowledgeSource> sources = managedSources();
        boolean synchronizedAny = false;
        boolean unchangedAny = false;
        for (ManagedKnowledgeSource source : sources) {
            KnowledgeSyncOutcome outcome = synchronize(source);
            synchronizedAny |= outcome == KnowledgeSyncOutcome.SYNCED;
            unchangedAny |= outcome == KnowledgeSyncOutcome.UNCHANGED;
        }
        if (synchronizedAny) {
            return KnowledgeSyncOutcome.SYNCED;
        }
        return unchangedAny
                ? KnowledgeSyncOutcome.UNCHANGED
                : KnowledgeSyncOutcome.ALREADY_RUNNING;
    }

    private KnowledgeSyncOutcome synchronize(ManagedKnowledgeSource source) {
        KnowledgeSourceFile sourceFile = source.file();
        String initialContentHash = hashService.sha256(sourceFile);
        KnowledgeSyncClaim claim = syncStateService.tryBeginSync(
                        source.sourceKey(),
                        sourceFile,
                        initialContentHash
                )
                .orElse(null);
        if (claim == null) {
            return KnowledgeSyncOutcome.ALREADY_RUNNING;
        }

        try {
            if (initialContentHash.equals(claim.previousContentHash())) {
                syncStateService.markUnchanged(claim);
                return KnowledgeSyncOutcome.UNCHANGED;
            }

            DentalKnowledgeDataset dataset = parser.parse(sourceFile.path());
            if (!source.sourceKey().equals(dataset.datasetId())) {
                throw unavailable("Dataset ID không khớp tên file đã cấu hình");
            }
            String verifiedContentHash = hashService.sha256(sourceFile);
            if (!initialContentHash.equals(verifiedContentHash)) {
                throw unavailable("Dataset đã thay đổi trong khi đang đồng bộ");
            }

            List<KnowledgeChunk> chunks = embeddingService.chunk(dataset);
            if (chunks.isEmpty()) {
                throw unavailable("Dataset không tạo được chunk embedding nào");
            }
            String embeddingModel = embeddingService.embeddingModel();
            int embeddingDimension = embeddingService.embeddingDimension();
            Set<String> storedRecordIds = vectorStorageService.findStoredRecordIds(
                    source.sourceKey(),
                    verifiedContentHash,
                    embeddingModel,
                    embeddingDimension
            );
            List<KnowledgeChunk> remainingChunks = remainingChunks(chunks, storedRecordIds);
            int processedChunkCount = chunks.size() - remainingChunks.size();
            syncStateService.recordProgress(
                    claim,
                    verifiedContentHash,
                    processedChunkCount,
                    chunks.size()
            );

            for (int start = 0; start < remainingChunks.size(); start += embeddingBatchSize) {
                if (start > 0) {
                    pauseBetweenBatches();
                }
                int end = Math.min(start + embeddingBatchSize, remainingChunks.size());
                List<KnowledgeChunk> batch = remainingChunks.subList(start, end);
                List<EmbeddedKnowledgeChunk> embeddedBatch = embeddingService.embedChunks(batch);
                if (embeddedBatch.size() != batch.size()) {
                    throw unavailable("Số chunk embedding không khớp batch đang đồng bộ");
                }
                vectorStorageService.storeStaging(
                        embeddedBatch,
                        verifiedContentHash,
                        claim.syncToken()
                );
                processedChunkCount += embeddedBatch.size();
                syncStateService.recordProgress(
                        claim,
                        verifiedContentHash,
                        processedChunkCount,
                        chunks.size()
                );
            }
            syncStateService.activate(new KnowledgeSyncActivation(
                    claim,
                    verifiedContentHash,
                    dataset.version(),
                    chunks.size(),
                    embeddingModel,
                    embeddingDimension,
                    dataset.clinicalReviewStatus(),
                    clock.instant()
            ));
            return KnowledgeSyncOutcome.SYNCED;
        } catch (RuntimeException exception) {
            handleFailure(claim, exception);
            if (exception instanceof KnowledgeBaseException knowledgeBaseException) {
                throw knowledgeBaseException;
            }
            throw unavailable("Không thể đồng bộ kho kiến thức");
        }
    }

    private List<ManagedKnowledgeSource> managedSources() {
        List<ManagedKnowledgeSource> sources = discoveryService.discover().stream()
                .map(source -> parser.managedSourceKey(source)
                        .map(sourceKey -> new ManagedKnowledgeSource(source, sourceKey)))
                .flatMap(java.util.Optional::stream)
                .toList();
        if (sources.isEmpty()) {
            throw unavailable("Không tìm thấy dataset nha khoa được hỗ trợ");
        }
        return sources;
    }

    private void handleFailure(KnowledgeSyncClaim claim, RuntimeException failure) {
        try {
            syncStateService.markFailed(claim, failure.getMessage());
        } catch (RuntimeException stateFailure) {
            failure.addSuppressed(stateFailure);
        }
    }

    private List<KnowledgeChunk> remainingChunks(
            List<KnowledgeChunk> chunks,
            Set<String> storedRecordIds
    ) {
        Set<String> datasetRecordIds = new HashSet<>();
        List<KnowledgeChunk> remaining = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            String recordId = chunk.metadata().recordId();
            if (recordId == null || recordId.isBlank() || !datasetRecordIds.add(recordId)) {
                throw unavailable("Dataset chứa recordId trống hoặc trùng lặp");
            }
            if (!storedRecordIds.contains(recordId)) {
                remaining.add(chunk);
            }
        }
        return List.copyOf(remaining);
    }

    private void pauseBetweenBatches() {
        if (embeddingBatchDelay.isZero()) {
            return;
        }
        try {
            Thread.sleep(embeddingBatchDelay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable("Tiến trình đồng bộ embedding đã bị gián đoạn");
        }
    }

    private KnowledgeBaseException unavailable(String message) {
        return new KnowledgeBaseException(message);
    }

    private record ManagedKnowledgeSource(
            KnowledgeSourceFile file,
            String sourceKey
    ) {
    }
}
