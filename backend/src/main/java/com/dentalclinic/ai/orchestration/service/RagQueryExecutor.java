package com.dentalclinic.ai.orchestration.service;

import com.dentalclinic.ai.conversation.model.ChatSemanticContext;
import com.dentalclinic.ai.gateway.AiCallExecutor;
import com.dentalclinic.ai.gateway.AiCallType;
import com.dentalclinic.ai.gateway.AiProviderFailureClassifier;
import com.dentalclinic.ai.knowledge.exception.KnowledgeBaseException;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalRequest;
import com.dentalclinic.ai.knowledge.service.KnowledgeRetrievalService;
import com.dentalclinic.ai.orchestration.model.RagQueryResult;
import com.dentalclinic.ai.telemetry.ChatTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RagQueryExecutor {

    private static final Logger log = LoggerFactory.getLogger(RagQueryExecutor.class);

    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final AiCallExecutor aiCallExecutor;

    public RagQueryExecutor(
            KnowledgeRetrievalService knowledgeRetrievalService,
            AiCallExecutor aiCallExecutor
    ) {
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.aiCallExecutor = aiCallExecutor;
    }

    public RagQueryResult execute(
            String query,
            ChatSemanticContext context,
            boolean required
    ) {
        if (!required) {
            return RagQueryResult.notRequired();
        }
        long startedNanos = System.nanoTime();
        try {
            var evidence = aiCallExecutor.execute(
                    AiCallType.RAG_RETRIEVAL,
                    () -> knowledgeRetrievalService.retrieve(
                            KnowledgeRetrievalRequest.of(buildKnowledgeQuery(query, context))
                    )
            );
            log.info(
                    "chat_rag_query_completed requestId={} durationMs={} available=true evidenceCount={}",
                    ChatTelemetry.currentRequestId(),
                    ChatTelemetry.elapsedMillis(startedNanos),
                    evidence.size()
            );
            return new RagQueryResult(true, evidence);
        } catch (KnowledgeBaseException exception) {
            log.warn(
                    "chat_rag_query_failed requestId={} durationMs={} available=false failureType=KNOWLEDGE_BASE",
                    ChatTelemetry.currentRequestId(),
                    ChatTelemetry.elapsedMillis(startedNanos)
            );
            return RagQueryResult.unavailable();
        } catch (RuntimeException exception) {
            AiProviderFailureClassifier.FailureType failureType =
                    AiProviderFailureClassifier.classify(exception);
            if (failureType == AiProviderFailureClassifier.FailureType.UNKNOWN) {
                throw exception;
            }
            log.warn(
                    "chat_rag_query_failed requestId={} durationMs={} available=false failureType={}",
                    ChatTelemetry.currentRequestId(),
                    ChatTelemetry.elapsedMillis(startedNanos),
                    failureType
            );
            return RagQueryResult.unavailable();
        }
    }

    private String buildKnowledgeQuery(String query, ChatSemanticContext context) {
        if (context.symptoms().isEmpty()) {
            return query;
        }
        return String.join(", ", context.symptoms()) + ". " + query;
    }
}
