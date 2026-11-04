package com.dentalclinic.ai.knowledge.model;

public record KnowledgeRetrievalRequest(
        String query,
        Integer topK,
        Double minimumSimilarity,
        KnowledgeRetrievalFilter filter
) {

    public KnowledgeRetrievalRequest {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Knowledge retrieval query must not be blank");
        }
        query = query.strip();
        if (topK != null && topK <= 0) {
            throw new IllegalArgumentException("Knowledge retrieval Top-K must be positive");
        }
        if (minimumSimilarity != null
                && (!Double.isFinite(minimumSimilarity)
                || minimumSimilarity < 0.0
                || minimumSimilarity > 1.0)) {
            throw new IllegalArgumentException("Minimum similarity must be between 0.0 and 1.0");
        }
        filter = filter == null ? KnowledgeRetrievalFilter.none() : filter;
    }

    public static KnowledgeRetrievalRequest of(String query) {
        return new KnowledgeRetrievalRequest(query, null, null, KnowledgeRetrievalFilter.none());
    }
}
