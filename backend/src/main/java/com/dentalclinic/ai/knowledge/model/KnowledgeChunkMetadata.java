package com.dentalclinic.ai.knowledge.model;

import java.util.List;

public record KnowledgeChunkMetadata(
        String sourceKey,
        String sourceVersion,
        String recordId,
        String title,
        String category,
        String language,
        List<String> symptoms,
        List<String> serviceCategoryHints,
        List<String> specializationHints,
        String sourceUrl,
        String clinicalReviewStatus
) {

    public KnowledgeChunkMetadata {
        symptoms = List.copyOf(symptoms);
        serviceCategoryHints = List.copyOf(serviceCategoryHints);
        specializationHints = List.copyOf(specializationHints);
    }
}
