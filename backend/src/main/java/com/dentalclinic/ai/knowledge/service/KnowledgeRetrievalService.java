package com.dentalclinic.ai.knowledge.service;

import com.dentalclinic.ai.embedding.EmbeddingGateway;
import com.dentalclinic.ai.gateway.AiProviderFailureClassifier;
import com.dentalclinic.ai.knowledge.config.KnowledgeRetrievalProperties;
import com.dentalclinic.ai.knowledge.constant.KnowledgeEmbeddingConstants;
import com.dentalclinic.ai.knowledge.exception.KnowledgeBaseException;
import com.dentalclinic.ai.knowledge.mapper.KnowledgeRetrievalResultMapper;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalCriteria;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalRequest;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalResult;
import com.dentalclinic.ai.knowledge.repository.KnowledgeRetrievalRepository;
import com.dentalclinic.ai.telemetry.ChatTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class KnowledgeRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalService.class);

    private final Optional<EmbeddingGateway> embeddingGateway;
    private final KnowledgeRetrievalRepository repository;
    private final KnowledgeRetrievalResultMapper mapper;
    private final KnowledgeRetrievalProperties properties;

    public KnowledgeRetrievalService(
            Optional<EmbeddingGateway> embeddingGateway,
            KnowledgeRetrievalRepository repository,
            KnowledgeRetrievalResultMapper mapper,
            KnowledgeRetrievalProperties properties
    ) {
        this.embeddingGateway = embeddingGateway;
        this.repository = repository;
        this.mapper = mapper;
        this.properties = properties;
    }

    public List<KnowledgeRetrievalResult> retrieve(KnowledgeRetrievalRequest request) {
        EmbeddingGateway gateway = embeddingGateway.orElseThrow(() -> new KnowledgeBaseException(
                "Embedding chưa được bật trên hệ thống"
        ));
        requireCompatibleGateway(gateway);
        int topK = request.topK() == null ? properties.defaultTopK() : request.topK();
        if (topK > properties.maxTopK()) {
            throw new IllegalArgumentException(
                    "Knowledge retrieval Top-K must not exceed " + properties.maxTopK()
            );
        }
        double minimumSimilarity = request.minimumSimilarity() == null
                ? properties.similarityThreshold()
                : request.minimumSimilarity();
        String requestId = ChatTelemetry.currentRequestId();
        long embeddingStartedNanos = System.nanoTime();
        float[] queryEmbedding;
        try {
            queryEmbedding = embedQuery(gateway, request.query());
            log.info(
                    "chat_embedding_completed requestId={} durationMs={} model={} dimension={}",
                    requestId,
                    ChatTelemetry.elapsedMillis(embeddingStartedNanos),
                    gateway.modelName(),
                    gateway.dimension()
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "chat_embedding_failed requestId={} durationMs={} model={} providerStatus={}",
                    requestId,
                    ChatTelemetry.elapsedMillis(embeddingStartedNanos),
                    gateway.modelName(),
                    AiProviderFailureClassifier.classify(exception)
            );
            throw exception;
        }

        KnowledgeRetrievalCriteria criteria = new KnowledgeRetrievalCriteria(
                queryEmbedding,
                gateway.modelName(),
                gateway.dimension(),
                topK,
                minimumSimilarity,
                request.filter()
        );
        long retrievalStartedNanos = System.nanoTime();
        try {
            List<KnowledgeRetrievalResult> results = repository.search(criteria).stream()
                    .map(mapper::toResult)
                    .toList();
            log.info(
                    "chat_vector_retrieval_completed requestId={} durationMs={} topK={} resultCount={}",
                    requestId,
                    ChatTelemetry.elapsedMillis(retrievalStartedNanos),
                    topK,
                    results.size()
            );
            return results;
        } catch (RuntimeException exception) {
            log.warn(
                    "chat_vector_retrieval_failed requestId={} durationMs={} providerStatus={}",
                    requestId,
                    ChatTelemetry.elapsedMillis(retrievalStartedNanos),
                    AiProviderFailureClassifier.classify(exception)
            );
            throw exception;
        }
    }

    private void requireCompatibleGateway(EmbeddingGateway gateway) {
        if (gateway.modelName() == null || gateway.modelName().isBlank()) {
            throw new KnowledgeBaseException("Embedding model không hợp lệ");
        }
        if (gateway.dimension() != KnowledgeEmbeddingConstants.VECTOR_DIMENSION) {
            throw new KnowledgeBaseException(
                    "Embedding truy vấn phải có đúng "
                            + KnowledgeEmbeddingConstants.VECTOR_DIMENSION
                            + " chiều"
            );
        }
    }

    private float[] embedQuery(EmbeddingGateway gateway, String query) {
        List<float[]> embeddings = gateway.embedAll(List.of(query));
        if (embeddings == null || embeddings.size() != 1) {
            throw new KnowledgeBaseException("Gemini không trả về đúng một embedding truy vấn");
        }
        float[] embedding = embeddings.getFirst();
        if (embedding == null || embedding.length != KnowledgeEmbeddingConstants.VECTOR_DIMENSION) {
            throw new KnowledgeBaseException(
                    "Embedding truy vấn phải có đúng "
                            + KnowledgeEmbeddingConstants.VECTOR_DIMENSION
                            + " chiều"
            );
        }
        for (float value : embedding) {
            if (!Float.isFinite(value)) {
                throw new KnowledgeBaseException("Embedding truy vấn chứa giá trị không hữu hạn");
            }
        }
        return embedding.clone();
    }
}
