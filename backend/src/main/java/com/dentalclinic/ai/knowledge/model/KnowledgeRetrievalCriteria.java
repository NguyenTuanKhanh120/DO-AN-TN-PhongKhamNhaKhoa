package com.dentalclinic.ai.knowledge.model;

import java.util.Objects;

public record KnowledgeRetrievalCriteria(
        float[] queryEmbedding,
        String embeddingModel,
        int embeddingDimension,
        int topK,
        double minimumSimilarity,
        KnowledgeRetrievalFilter filter
) {

    public KnowledgeRetrievalCriteria {
        Objects.requireNonNull(queryEmbedding, "queryEmbedding must not be null");
        Objects.requireNonNull(filter, "filter must not be null");
        if (embeddingModel == null || embeddingModel.isBlank()) {
            throw new IllegalArgumentException("embeddingModel must not be blank");
        }
        if (embeddingDimension <= 0 || queryEmbedding.length != embeddingDimension) {
            throw new IllegalArgumentException("Query embedding dimension is invalid");
        }
        for (float value : queryEmbedding) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Query embedding must contain only finite values");
            }
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("Retrieval Top-K must be positive");
        }
        if (!Double.isFinite(minimumSimilarity)
                || minimumSimilarity < 0.0
                || minimumSimilarity > 1.0) {
            throw new IllegalArgumentException("Minimum similarity must be between 0.0 and 1.0");
        }
        queryEmbedding = queryEmbedding.clone();
    }

    @Override
    public float[] queryEmbedding() {
        return queryEmbedding.clone();
    }
}
