package com.dentalclinic.ai.knowledge.service;

import com.dentalclinic.ai.knowledge.config.KnowledgeRetrievalProperties;
import com.dentalclinic.ai.knowledge.constant.KnowledgeEmbeddingConstants;
import com.dentalclinic.ai.knowledge.dto.KnowledgeSearchFilterRequest;
import com.dentalclinic.ai.knowledge.dto.KnowledgeSearchTestItemResponse;
import com.dentalclinic.ai.knowledge.dto.KnowledgeSearchTestRequest;
import com.dentalclinic.ai.knowledge.dto.KnowledgeSearchTestResponse;
import com.dentalclinic.ai.knowledge.dto.KnowledgeSourceStatusResponse;
import com.dentalclinic.ai.knowledge.dto.KnowledgeStatusResponse;
import com.dentalclinic.ai.knowledge.dto.KnowledgeSyncResponse;
import com.dentalclinic.ai.knowledge.entity.KnowledgeSource;
import com.dentalclinic.ai.knowledge.entity.KnowledgeSourceStatus;
import com.dentalclinic.ai.knowledge.exception.KnowledgeBaseException;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalFilter;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalRequest;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalResult;
import com.dentalclinic.ai.knowledge.model.KnowledgeSyncOutcome;
import com.dentalclinic.ai.knowledge.repository.KnowledgeSourceRepository;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class AdminKnowledgeService {

    private final KnowledgeSourceRepository sourceRepository;
    private final KnowledgeBaseSyncService syncService;
    private final KnowledgeRetrievalService retrievalService;
    private final KnowledgeRetrievalProperties retrievalProperties;
    private final Clock clock;
    private final boolean aiEnabled;

    public AdminKnowledgeService(
            KnowledgeSourceRepository sourceRepository,
            KnowledgeBaseSyncService syncService,
            KnowledgeRetrievalService retrievalService,
            KnowledgeRetrievalProperties retrievalProperties,
            Clock clock,
            @Value("${app.ai.enabled:false}") boolean aiEnabled
    ) {
        this.sourceRepository = sourceRepository;
        this.syncService = syncService;
        this.retrievalService = retrievalService;
        this.retrievalProperties = retrievalProperties;
        this.clock = clock;
        this.aiEnabled = aiEnabled;
    }

    @Transactional(readOnly = true)
    public KnowledgeStatusResponse getStatus() {
        List<KnowledgeSource> sources = sourceRepository.findAllByOrderBySourceKeyAsc();
        List<KnowledgeSourceStatusResponse> sourceResponses = sources.stream()
                .map(this::toStatusResponse)
                .toList();
        long successfulSources = countStatus(sources, KnowledgeSourceStatus.SUCCESS);
        long syncingSources = countStatus(sources, KnowledgeSourceStatus.SYNCING);
        long failedSources = countStatus(sources, KnowledgeSourceStatus.FAILED);
        long pendingSources = countStatus(sources, KnowledgeSourceStatus.PENDING);
        long totalChunks = sources.stream().mapToLong(KnowledgeSource::getChunkCount).sum();
        Instant lastSuccessfulSyncAt = sources.stream()
                .map(KnowledgeSource::getLastSuccessAt)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
        boolean ready = aiEnabled && sources.stream().anyMatch(this::isReadySource);

        return new KnowledgeStatusResponse(
                aiEnabled,
                ready,
                sources.size(),
                successfulSources,
                syncingSources,
                failedSources,
                pendingSources,
                totalChunks,
                lastSuccessfulSyncAt,
                sourceResponses
        );
    }

    public KnowledgeSyncResponse synchronize() {
        requireAiEnabled();
        KnowledgeSyncOutcome outcome = syncService.synchronizePrimaryDataset();
        return new KnowledgeSyncResponse(outcome, clock.instant());
    }

    public KnowledgeSearchTestResponse searchTest(KnowledgeSearchTestRequest request) {
        requireAiEnabled();
        int topK = request.topK() == null
                ? retrievalProperties.defaultTopK()
                : request.topK();
        if (topK > retrievalProperties.maxTopK()) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Top-K không được vượt quá " + retrievalProperties.maxTopK()
            );
        }
        double minimumSimilarity = request.minimumSimilarity() == null
                ? retrievalProperties.similarityThreshold()
                : request.minimumSimilarity();
        KnowledgeRetrievalFilter filter = toFilter(request.filter());
        String query = request.query().strip();
        List<KnowledgeSearchTestItemResponse> results = retrievalService.retrieve(
                        new KnowledgeRetrievalRequest(
                                query,
                                topK,
                                minimumSimilarity,
                                filter
                        )
                ).stream()
                .map(this::toSearchResult)
                .toList();
        return new KnowledgeSearchTestResponse(
                query,
                topK,
                minimumSimilarity,
                results.size(),
                results
        );
    }

    private long countStatus(List<KnowledgeSource> sources, KnowledgeSourceStatus status) {
        return sources.stream().filter(source -> source.getStatus() == status).count();
    }

    private boolean isReadySource(KnowledgeSource source) {
        return source.getStatus() == KnowledgeSourceStatus.SUCCESS
                && source.getChunkCount() > 0
                && source.getEmbeddingModel() != null
                && !source.getEmbeddingModel().isBlank()
                && source.getEmbeddingDimension() != null
                && source.getEmbeddingDimension() == KnowledgeEmbeddingConstants.VECTOR_DIMENSION;
    }

    private KnowledgeSourceStatusResponse toStatusResponse(KnowledgeSource source) {
        return new KnowledgeSourceStatusResponse(
                source.getSourceKey(),
                source.getFileName(),
                source.getFileType(),
                source.getSourceVersion(),
                source.getStatus(),
                source.getChunkCount(),
                source.getEmbeddingModel(),
                source.getEmbeddingDimension(),
                source.getClinicalReviewStatus(),
                source.getLastSyncedAt(),
                source.getLastSuccessAt(),
                source.getLastError()
        );
    }

    private KnowledgeRetrievalFilter toFilter(KnowledgeSearchFilterRequest filter) {
        return filter == null
                ? KnowledgeRetrievalFilter.none()
                : new KnowledgeRetrievalFilter(
                        filter.sourceKey(),
                        filter.category(),
                        filter.language(),
                        filter.clinicalReviewStatus()
                );
    }

    private KnowledgeSearchTestItemResponse toSearchResult(KnowledgeRetrievalResult result) {
        return new KnowledgeSearchTestItemResponse(
                result.content(),
                result.category(),
                result.language(),
                result.symptoms(),
                result.serviceCategoryHints(),
                result.specializationHints(),
                result.source().sourceKey(),
                result.source().sourceVersion(),
                result.source().recordId(),
                result.source().title(),
                result.source().sourceUrl(),
                result.score()
        );
    }

    private void requireAiEnabled() {
        if (!aiEnabled) {
            throw new KnowledgeBaseException("AI chưa được bật trên hệ thống");
        }
    }
}
