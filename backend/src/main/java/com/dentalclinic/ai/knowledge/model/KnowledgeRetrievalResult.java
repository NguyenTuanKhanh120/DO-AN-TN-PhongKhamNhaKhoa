package com.dentalclinic.ai.knowledge.model;

import java.util.List;
import java.util.UUID;

public record KnowledgeRetrievalResult(
        UUID documentId,
        String content,
        String category,
        String language,
        List<String> symptoms,
        List<String> serviceCategoryHints,
        List<String> specializationHints,
        KnowledgeSourceTrace source,
        double score
) {

    public KnowledgeRetrievalResult {
        symptoms = List.copyOf(symptoms);
        serviceCategoryHints = List.copyOf(serviceCategoryHints);
        specializationHints = List.copyOf(specializationHints);
    }
}
