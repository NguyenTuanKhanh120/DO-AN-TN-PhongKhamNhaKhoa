package com.dentalclinic.ai.knowledge.model;

public record KnowledgeRetrievalFilter(
        String sourceKey,
        String category,
        String language,
        String clinicalReviewStatus
) {

    public KnowledgeRetrievalFilter {
        sourceKey = normalize(sourceKey);
        category = normalize(category);
        language = normalize(language);
        clinicalReviewStatus = normalize(clinicalReviewStatus);
    }

    public static KnowledgeRetrievalFilter none() {
        return new KnowledgeRetrievalFilter(null, null, null, null);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
