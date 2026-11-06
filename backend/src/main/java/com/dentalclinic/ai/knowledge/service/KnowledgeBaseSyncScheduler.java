package com.dentalclinic.ai.knowledge.service;

import com.dentalclinic.ai.knowledge.model.KnowledgeSyncOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
public class KnowledgeBaseSyncScheduler {

    private final KnowledgeBaseSyncService syncService;

    public KnowledgeBaseSyncScheduler(KnowledgeBaseSyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(
            cron = "${app.ai.knowledge-base.sync-cron:0 */15 * * * *}",
            zone = "${clinic.timezone:Asia/Ho_Chi_Minh}"
    )
    public void checkDatasets() {
        try {
            KnowledgeSyncOutcome outcome = syncService.synchronizeAllDatasets();
            log.info("Knowledge base sync check completed: outcome={}", outcome);
        } catch (RuntimeException exception) {
            log.error(
                    "Knowledge base sync check failed: exceptionType={}",
                    exception.getClass().getSimpleName()
            );
        }
    }
}
