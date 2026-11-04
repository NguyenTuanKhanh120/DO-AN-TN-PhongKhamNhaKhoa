package com.dentalclinic.ai.knowledge;

import com.dentalclinic.ai.knowledge.config.KnowledgeRetrievalProperties;
import com.dentalclinic.ai.knowledge.dto.KnowledgeSearchFilterRequest;
import com.dentalclinic.ai.knowledge.dto.KnowledgeSearchTestRequest;
import com.dentalclinic.ai.knowledge.entity.KnowledgeFileType;
import com.dentalclinic.ai.knowledge.entity.KnowledgeSource;
import com.dentalclinic.ai.knowledge.entity.KnowledgeSourceStatus;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalRequest;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalResult;
import com.dentalclinic.ai.knowledge.model.KnowledgeSourceTrace;
import com.dentalclinic.ai.knowledge.model.KnowledgeSyncOutcome;
import com.dentalclinic.ai.knowledge.repository.KnowledgeSourceRepository;
import com.dentalclinic.ai.knowledge.service.AdminKnowledgeService;
import com.dentalclinic.ai.knowledge.service.KnowledgeBaseSyncService;
import com.dentalclinic.ai.knowledge.service.KnowledgeRetrievalService;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminKnowledgeServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-30T01:00:00Z");

    private final KnowledgeSourceRepository sourceRepository = mock(KnowledgeSourceRepository.class);
    private final KnowledgeBaseSyncService syncService = mock(KnowledgeBaseSyncService.class);
    private final KnowledgeRetrievalService retrievalService = mock(KnowledgeRetrievalService.class);
    private final KnowledgeRetrievalProperties properties = new KnowledgeRetrievalProperties(
            5,
            20,
            0.55
    );
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldReturnSanitizedKnowledgeStatusSummary() {
        KnowledgeSource successful = source(
                "nidcr-dental-knowledge-vi",
                KnowledgeSourceStatus.SUCCESS,
                42
        );
        successful.setEmbeddingModel("gemini-embedding-2");
        successful.setEmbeddingDimension(768);
        successful.setLastSuccessAt(NOW.minusSeconds(60));
        successful.setContentHash("a".repeat(64));
        successful.setSyncToken(java.util.UUID.randomUUID());
        KnowledgeSource failed = source("failed-source", KnowledgeSourceStatus.FAILED, 0);
        failed.setLastError("Dataset không hợp lệ");
        when(sourceRepository.findAllByOrderBySourceKeyAsc())
                .thenReturn(List.of(failed, successful));

        var response = service(true).getStatus();

        assertThat(response.aiEnabled()).isTrue();
        assertThat(response.ready()).isTrue();
        assertThat(response.totalSources()).isEqualTo(2);
        assertThat(response.successfulSources()).isEqualTo(1);
        assertThat(response.failedSources()).isEqualTo(1);
        assertThat(response.totalChunks()).isEqualTo(42);
        assertThat(response.lastSuccessfulSyncAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(response.sources()).extracting(source -> source.sourceKey())
                .containsExactly("failed-source", "nidcr-dental-knowledge-vi");
        assertThat(response.sources().getFirst().lastError()).isEqualTo("Dataset không hợp lệ");
    }

    @Test
    void shouldReportNotReadyWhenAiIsDisabled() {
        KnowledgeSource source = source("source", KnowledgeSourceStatus.SUCCESS, 10);
        source.setEmbeddingModel("gemini-embedding-2");
        source.setEmbeddingDimension(768);
        when(sourceRepository.findAllByOrderBySourceKeyAsc()).thenReturn(List.of(source));

        var response = service(false).getStatus();

        assertThat(response.aiEnabled()).isFalse();
        assertThat(response.ready()).isFalse();
    }

    @Test
    void shouldSynchronizePrimaryDatasetWithoutWrappingProviderCallInTransaction() {
        when(syncService.synchronizePrimaryDataset()).thenReturn(KnowledgeSyncOutcome.SYNCED);

        var response = service(true).synchronize();

        assertThat(response.outcome()).isEqualTo(KnowledgeSyncOutcome.SYNCED);
        assertThat(response.processedAt()).isEqualTo(NOW);
        verify(syncService).synchronizePrimaryDataset();
    }

    @Test
    void shouldRejectSyncAndSearchBeforeMutationWhenAiIsDisabled() {
        AdminKnowledgeService service = service(false);
        KnowledgeSearchTestRequest request = new KnowledgeSearchTestRequest(
                "đau răng",
                null,
                null,
                null
        );

        assertUnavailable(service::synchronize);
        assertUnavailable(() -> service.searchTest(request));
        verify(syncService, never()).synchronizePrimaryDataset();
        verify(retrievalService, never()).retrieve(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldSearchWithConfiguredDefaultsAndMapTraceableResults() {
        KnowledgeSearchTestRequest request = new KnowledgeSearchTestRequest(
                "  đau răng  ",
                null,
                null,
                new KnowledgeSearchFilterRequest(
                        "nidcr-dental-knowledge-vi",
                        "tooth-decay",
                        "vi",
                        "REVIEWED"
                )
        );
        KnowledgeRetrievalResult result = new KnowledgeRetrievalResult(
                java.util.UUID.randomUUID(),
                "Sâu răng có thể gây đau hoặc ê buốt.",
                "tooth-decay",
                "vi",
                List.of("đau răng"),
                List.of("Khám tổng quát"),
                List.of("Nha khoa tổng quát"),
                new KnowledgeSourceTrace(
                        "nidcr-dental-knowledge-vi",
                        "1.0.0",
                        "tooth-decay-001",
                        "Sâu răng",
                        "https://www.nidcr.nih.gov/health-info/tooth-decay"
                ),
                0.88
        );
        when(retrievalService.retrieve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(result));

        var response = service(true).searchTest(request);

        assertThat(response.query()).isEqualTo("đau răng");
        assertThat(response.topK()).isEqualTo(5);
        assertThat(response.minimumSimilarity()).isEqualTo(0.55);
        assertThat(response.resultCount()).isEqualTo(1);
        assertThat(response.results().getFirst().recordId()).isEqualTo("tooth-decay-001");
        assertThat(response.results().getFirst().score()).isEqualTo(0.88);

        ArgumentCaptor<KnowledgeRetrievalRequest> captor = ArgumentCaptor.forClass(
                KnowledgeRetrievalRequest.class
        );
        verify(retrievalService).retrieve(captor.capture());
        assertThat(captor.getValue().filter().sourceKey())
                .isEqualTo("nidcr-dental-knowledge-vi");
        assertThat(captor.getValue().filter().clinicalReviewStatus()).isEqualTo("REVIEWED");
    }

    @Test
    void shouldRejectTopKAboveConfiguredMaximum() {
        KnowledgeSearchTestRequest request = new KnowledgeSearchTestRequest(
                "đau răng",
                21,
                0.6,
                null
        );

        assertThatThrownBy(() -> service(true).searchTest(request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST)
                );
        verify(retrievalService, never()).retrieve(org.mockito.ArgumentMatchers.any());
    }

    private AdminKnowledgeService service(boolean aiEnabled) {
        return new AdminKnowledgeService(
                sourceRepository,
                syncService,
                retrievalService,
                properties,
                clock,
                aiEnabled
        );
    }

    private KnowledgeSource source(
            String sourceKey,
            KnowledgeSourceStatus status,
            int chunkCount
    ) {
        KnowledgeSource source = new KnowledgeSource();
        source.setSourceKey(sourceKey);
        source.setFileName(sourceKey + ".json");
        source.setFileType(KnowledgeFileType.JSON);
        source.setSourceVersion("1.0.0");
        source.setStatus(status);
        source.setChunkCount(chunkCount);
        source.setClinicalReviewStatus("REVIEWED");
        return source;
    }

    private void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.KNOWLEDGE_BASE_UNAVAILABLE)
                );
    }
}
