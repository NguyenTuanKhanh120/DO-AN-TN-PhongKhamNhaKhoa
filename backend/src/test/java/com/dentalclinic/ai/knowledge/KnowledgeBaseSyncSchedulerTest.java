package com.dentalclinic.ai.knowledge;

import com.dentalclinic.ai.knowledge.exception.KnowledgeBaseException;
import com.dentalclinic.ai.knowledge.model.KnowledgeSyncOutcome;
import com.dentalclinic.ai.knowledge.service.KnowledgeBaseSyncScheduler;
import com.dentalclinic.ai.knowledge.service.KnowledgeBaseSyncService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeBaseSyncSchedulerTest {

    private final KnowledgeBaseSyncService syncService = mock(KnowledgeBaseSyncService.class);
    private final KnowledgeBaseSyncScheduler scheduler = new KnowledgeBaseSyncScheduler(syncService);

    @Test
    void shouldCheckPrimaryDatasetOnScheduledRun() {
        when(syncService.synchronizePrimaryDataset()).thenReturn(KnowledgeSyncOutcome.UNCHANGED);

        scheduler.checkPrimaryDataset();

        verify(syncService).synchronizePrimaryDataset();
    }

    @Test
    void shouldContainFailureSoSchedulerCanRunAgain() {
        when(syncService.synchronizePrimaryDataset())
                .thenThrow(new KnowledgeBaseException("test failure"));

        scheduler.checkPrimaryDataset();

        verify(syncService).synchronizePrimaryDataset();
    }
}
