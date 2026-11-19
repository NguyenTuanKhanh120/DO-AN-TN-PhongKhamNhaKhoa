package com.dentalclinic.ai.orchestration.service;

import com.dentalclinic.ai.conversation.model.ChatContextResolution;
import com.dentalclinic.ai.orchestration.model.ChatGroundingResult;
import com.dentalclinic.ai.orchestration.model.ChatQueryPlan;
import com.dentalclinic.ai.orchestration.model.DatabaseGroundingContext;
import com.dentalclinic.ai.orchestration.model.DatabaseQueryResult;
import com.dentalclinic.ai.orchestration.model.RagQueryResult;
import com.dentalclinic.ai.telemetry.ChatTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChatGroundingService {

    private static final Logger log = LoggerFactory.getLogger(ChatGroundingService.class);

    private final DatabaseQueryExecutor databaseQueryExecutor;
    private final RagQueryExecutor ragQueryExecutor;
    private final ChatQueryPlanner queryPlanner;

    public ChatGroundingService(
            DatabaseQueryExecutor databaseQueryExecutor,
            RagQueryExecutor ragQueryExecutor,
            ChatQueryPlanner queryPlanner
    ) {
        this.databaseQueryExecutor = databaseQueryExecutor;
        this.ragQueryExecutor = ragQueryExecutor;
        this.queryPlanner = queryPlanner;
    }

    public ChatGroundingResult resolve(String query, ChatContextResolution resolution) {
        long groundingStartedNanos = System.nanoTime();
        String requestId = ChatTelemetry.currentRequestId();
        ChatQueryPlan queryPlan = queryPlanner.plan(resolution);

        long databasePrepareStartedNanos = System.nanoTime();
        DatabaseGroundingContext prepared = databaseQueryExecutor.prepare(resolution);
        long databaseNanos = System.nanoTime() - databasePrepareStartedNanos;

        long knowledgeStartedNanos = System.nanoTime();
        RagQueryResult ragResult = ragQueryExecutor.execute(
                query,
                prepared.context(),
                queryPlan.requiresRag()
        );
        long knowledgeMs = queryPlan.requiresRag()
                ? ChatTelemetry.elapsedMillis(knowledgeStartedNanos)
                : 0L;

        long databaseExecuteStartedNanos = System.nanoTime();
        DatabaseQueryResult databaseResult = databaseQueryExecutor.execute(
                prepared,
                ragResult.evidence()
        );
        databaseNanos += System.nanoTime() - databaseExecuteStartedNanos;

        ChatGroundingResult result = new ChatGroundingResult(
                prepared.context(),
                queryPlan.requiresRag(),
                ragResult.available(),
                ragResult.evidence(),
                databaseResult.services(),
                databaseResult.doctors(),
                databaseResult.slots()
        );
        log.info(
                "chat_grounding_completed requestId={} route={} intents={} totalMs={} databaseMs={} knowledgeMs={} knowledgeAvailable={} evidenceCount={} serviceCount={} doctorCount={} slotCount={}",
                requestId,
                queryPlan.route(),
                queryPlan.intents(),
                ChatTelemetry.elapsedMillis(groundingStartedNanos),
                Math.max(0L, databaseNanos / 1_000_000L),
                knowledgeMs,
                ragResult.available(),
                ragResult.evidence().size(),
                result.services().size(),
                result.doctors().size(),
                result.slots().size()
        );
        return result;
    }
}
