package com.dentalclinic.ai.knowledge.service;

import com.dentalclinic.ai.knowledge.constant.KnowledgeEmbeddingConstants;
import com.dentalclinic.ai.knowledge.entity.KnowledgeSource;
import com.dentalclinic.ai.knowledge.entity.KnowledgeSourceStatus;
import com.dentalclinic.ai.knowledge.exception.KnowledgeBaseException;
import com.dentalclinic.ai.knowledge.model.KnowledgeSourceFile;
import com.dentalclinic.ai.knowledge.model.KnowledgeSyncActivation;
import com.dentalclinic.ai.knowledge.model.KnowledgeSyncClaim;
import com.dentalclinic.ai.knowledge.repository.KnowledgeSourceRepository;
import com.dentalclinic.ai.knowledge.repository.KnowledgeVectorDocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class KnowledgeSyncStateService {

    private static final int MAX_ERROR_LENGTH = 2000;
    private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");

    private final KnowledgeSourceRepository sourceRepository;
    private final KnowledgeVectorDocumentRepository vectorRepository;
    private final Clock clock;
    private final Duration syncLockTimeout;

    public KnowledgeSyncStateService(
            KnowledgeSourceRepository sourceRepository,
            KnowledgeVectorDocumentRepository vectorRepository,
            Clock clock,
            @Value("${app.ai.knowledge-base.sync-lock-timeout:30m}") Duration syncLockTimeout
    ) {
        if (syncLockTimeout == null || syncLockTimeout.isZero() || syncLockTimeout.isNegative()) {
            throw new IllegalArgumentException("Knowledge sync lock timeout must be positive");
        }
        this.sourceRepository = sourceRepository;
        this.vectorRepository = vectorRepository;
        this.clock = clock;
        this.syncLockTimeout = syncLockTimeout;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<KnowledgeSyncClaim> tryBeginSync(
            String sourceKey,
            KnowledgeSourceFile sourceFile,
            String contentHash
    ) {
        if (contentHash == null || !SHA_256.matcher(contentHash).matches()) {
            throw new IllegalArgumentException("contentHash must be a lowercase SHA-256 value");
        }
        Instant attemptedAt = clock.instant();
        UUID syncToken = UUID.randomUUID();
        int claimed = sourceRepository.tryBeginSync(
                sourceKey,
                sourceFile.fileName(),
                sourceFile.fileType().name(),
                contentHash,
                attemptedAt,
                attemptedAt.minus(syncLockTimeout),
                syncToken
        );
        if (claimed == 0) {
            return Optional.empty();
        }

        KnowledgeSource source = sourceRepository.findBySourceKey(sourceKey)
                .orElseThrow(() -> unavailable("Không tìm thấy trạng thái nguồn kiến thức vừa khóa"));
        if (!syncToken.equals(source.getSyncToken())) {
            throw unavailable("Không thể xác nhận quyền sở hữu tiến trình đồng bộ");
        }
        return Optional.of(new KnowledgeSyncClaim(
                sourceKey,
                syncToken,
                source.getContentHash()
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markUnchanged(KnowledgeSyncClaim claim) {
        KnowledgeSource source = requireOwnedSource(claim);
        source.setStatus(KnowledgeSourceStatus.SUCCESS);
        source.setLastError(null);
        source.setSyncToken(null);
        clearProgress(source);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordProgress(
            KnowledgeSyncClaim claim,
            String contentHash,
            int processedChunkCount,
            int totalChunkCount
    ) {
        if (contentHash == null
                || !SHA_256.matcher(contentHash).matches()
                || processedChunkCount < 0
                || totalChunkCount <= 0
                || processedChunkCount > totalChunkCount) {
            throw new IllegalArgumentException("Knowledge sync progress is invalid");
        }
        KnowledgeSource source = requireOwnedSource(claim);
        source.setPendingContentHash(contentHash);
        source.setProcessedChunkCount(processedChunkCount);
        source.setTotalChunkCount(totalChunkCount);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(KnowledgeSyncClaim claim, String failureMessage) {
        sourceRepository.findBySourceKeyForUpdate(claim.sourceKey())
                .filter(source -> claim.syncToken().equals(source.getSyncToken()))
                .ifPresent(source -> {
                    source.setStatus(KnowledgeSourceStatus.FAILED);
                    source.setLastError(sanitizeFailure(failureMessage));
                    source.setSyncToken(null);
                });
    }

    @Transactional
    public void activate(KnowledgeSyncActivation activation) {
        requireActivation(activation);
        KnowledgeSyncClaim claim = activation.claim();
        long validVectorCount = vectorRepository.countValidStagingDocuments(
                claim.sourceKey(),
                activation.contentHash(),
                activation.embeddingModel(),
                activation.embeddingDimension()
        );
        if (validVectorCount != activation.chunkCount()) {
            throw unavailable(
                    "Không thể kích hoạt phiên bản vector: mong đợi "
                            + activation.chunkCount()
                            + " chunk nhưng xác minh được "
                            + validVectorCount
            );
        }

        KnowledgeSource source = requireOwnedSource(claim);
        source.setContentHash(activation.contentHash());
        source.setSourceVersion(activation.sourceVersion());
        source.setChunkCount(activation.chunkCount());
        source.setEmbeddingModel(activation.embeddingModel());
        source.setEmbeddingDimension(activation.embeddingDimension());
        source.setClinicalReviewStatus(activation.clinicalReviewStatus());
        source.setStatus(KnowledgeSourceStatus.SUCCESS);
        source.setLastSuccessAt(activation.completedAt());
        source.setLastError(null);
        source.setSyncToken(null);
        clearProgress(source);
        sourceRepository.save(source);

        vectorRepository.deleteOtherVersions(
                claim.sourceKey(),
                activation.contentHash(),
                activation.embeddingModel(),
                Integer.toString(activation.embeddingDimension())
        );
    }

    private KnowledgeSource requireOwnedSource(KnowledgeSyncClaim claim) {
        KnowledgeSource source = sourceRepository.findBySourceKeyForUpdate(claim.sourceKey())
                .orElseThrow(() -> unavailable("Không tìm thấy nguồn kiến thức cần đồng bộ"));
        if (source.getStatus() != KnowledgeSourceStatus.SYNCING
                || !claim.syncToken().equals(source.getSyncToken())) {
            throw unavailable("Tiến trình đồng bộ không còn sở hữu khóa của nguồn kiến thức");
        }
        return source;
    }

    private void requireActivation(KnowledgeSyncActivation activation) {
        if (activation == null
                || activation.claim() == null
                || activation.contentHash() == null
                || !SHA_256.matcher(activation.contentHash()).matches()
                || activation.sourceVersion() == null
                || activation.sourceVersion().isBlank()
                || activation.chunkCount() <= 0
                || activation.embeddingModel() == null
                || activation.embeddingModel().isBlank()
                || activation.embeddingDimension() != KnowledgeEmbeddingConstants.VECTOR_DIMENSION
                || activation.clinicalReviewStatus() == null
                || activation.clinicalReviewStatus().isBlank()
                || activation.completedAt() == null) {
            throw new IllegalArgumentException("Knowledge sync activation is invalid");
        }
    }

    private String sanitizeFailure(String failureMessage) {
        String normalized = failureMessage == null || failureMessage.isBlank()
                ? "Không thể đồng bộ kho kiến thức"
                : failureMessage.replaceAll("[\\r\\n\\t]+", " ").strip();
        return normalized.length() <= MAX_ERROR_LENGTH
                ? normalized
                : normalized.substring(0, MAX_ERROR_LENGTH);
    }

    private void clearProgress(KnowledgeSource source) {
        source.setPendingContentHash(null);
        source.setProcessedChunkCount(0);
        source.setTotalChunkCount(0);
    }

    private KnowledgeBaseException unavailable(String message) {
        return new KnowledgeBaseException(message);
    }
}
