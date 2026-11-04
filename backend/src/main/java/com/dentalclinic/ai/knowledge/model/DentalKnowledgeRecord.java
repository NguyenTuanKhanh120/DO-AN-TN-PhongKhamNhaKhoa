package com.dentalclinic.ai.knowledge.model;

import java.util.List;

public record DentalKnowledgeRecord(
        String id,
        String slug,
        String title,
        String category,
        String content,
        List<String> symptoms,
        List<String> possibleFactors,
        List<String> careKnowledge,
        List<String> serviceCategoryHints,
        List<String> specializationHints,
        List<String> keywords,
        DentalKnowledgeSource source
) {

    public DentalKnowledgeRecord {
        symptoms = List.copyOf(symptoms);
        possibleFactors = List.copyOf(possibleFactors);
        careKnowledge = List.copyOf(careKnowledge);
        serviceCategoryHints = List.copyOf(serviceCategoryHints);
        specializationHints = List.copyOf(specializationHints);
        keywords = List.copyOf(keywords);
    }
}
