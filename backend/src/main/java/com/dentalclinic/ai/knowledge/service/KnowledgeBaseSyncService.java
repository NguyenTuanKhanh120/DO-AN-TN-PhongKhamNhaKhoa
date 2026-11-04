package com.dentalclinic.ai.knowledge.service;

import com.dentalclinic.ai.knowledge.exception.KnowledgeBaseException;
import com.dentalclinic.ai.knowledge.model.DentalKnowledgeDataset;
import com.dentalclinic.ai.knowledge.model.EmbeddedKnowledgeChunk;
import com.dentalclinic.ai.knowledge.model.KnowledgeSourceFile;
import com.dentalclinic.ai.knowledge.model.KnowledgeSyncActivation;
import com.dentalclinic.ai.knowledge.model.KnowledgeSyncClaim;
import com.dentalclinic.ai.knowledge.model.KnowledgeSyncOutcome;
import com.dentalclinic.ai.knowledge.parser.NidcrDentalKnowledgeDatasetValidator;
import com.dentalclinic.ai.knowledge.parser.NidcrDentalKnowledgeJsonParser;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

@Service
public class KnowledgeBaseSyncService {

    private final KnowledgeBaseDiscoveryService discoveryService;
    private final KnowledgeContentHashService hashService;
    private final NidcrDentalKnowledgeJsonParser parser;
    private final KnowledgeEmbeddingService embeddingService;
    private final KnowledgeVectorStorageService vectorStorageService;
    private final KnowledgeSyncStateService syncStateService;
    private final Clock clock;

    public KnowledgeBaseSyncService(
            KnowledgeBaseDiscoveryService discoveryService,
            KnowledgeContentHashService hashService,
            NidcrDentalKnowledgeJsonParser parser,
            KnowledgeEmbeddingService embeddingService,
            KnowledgeVectorStorageService vectorStorageService,
            KnowledgeSyncStateService syncStateService,
            Clock clock
    ) {
        this.discoveryService = discoveryService;
        this.hashService = hashService;
        this.parser = parser;
        this.embeddingService = embeddingService;
        this.vectorStorageService = vectorStorageService;
        this.syncStateService = syncStateService;
        this.clock = clock;
    }

    public KnowledgeSyncOutcome synchronizePrimaryDataset() {
        KnowledgeSourceFile sourceFile = primaryDataset();
        String initialContentHash = hashService.sha256(sourceFile);
        KnowledgeSyncClaim claim = syncStateService.tryBeginSync(
                        NidcrDentalKnowledgeDatasetValidator.EXPECTED_DATASET_ID,
                        sourceFile
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
            String verifiedContentHash = hashService.sha256(sourceFile);
            if (!initialContentHash.equals(verifiedContentHash)) {
                throw unavailable("Dataset đã thay đổi trong khi đang đồng bộ");
            }

            List<EmbeddedKnowledgeChunk> embeddedChunks = embeddingService.embed(dataset);
            if (embeddedChunks.isEmpty()) {
                throw unavailable("Dataset không tạo được chunk embedding nào");
            }

            vectorStorageService.storeStaging(
                    embeddedChunks,
                    verifiedContentHash,
                    claim.syncToken()
            );
            EmbeddedKnowledgeChunk firstChunk = embeddedChunks.getFirst();
            syncStateService.activate(new KnowledgeSyncActivation(
                    claim,
                    verifiedContentHash,
                    dataset.version(),
                    embeddedChunks.size(),
                    firstChunk.embeddingModel(),
                    firstChunk.embeddingDimension(),
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

    private KnowledgeSourceFile primaryDataset() {
        return discoveryService.discover().stream()
                .filter(source -> NidcrDentalKnowledgeJsonParser.PRIMARY_DATASET_FILE_NAME
                        .equals(source.fileName()))
                .findFirst()
                .orElseThrow(() -> unavailable("Không tìm thấy dataset nha khoa chính"));
    }

    private void handleFailure(KnowledgeSyncClaim claim, RuntimeException failure) {
        try {
            vectorStorageService.deleteStaging(claim.sourceKey(), claim.syncToken());
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
        try {
            syncStateService.markFailed(claim, failure.getMessage());
        } catch (RuntimeException stateFailure) {
            failure.addSuppressed(stateFailure);
        }
    }

    private KnowledgeBaseException unavailable(String message) {
        return new KnowledgeBaseException(message);
    }
}
