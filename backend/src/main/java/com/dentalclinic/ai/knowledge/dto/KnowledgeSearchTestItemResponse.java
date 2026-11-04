package com.dentalclinic.ai.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Một đoạn kiến thức được truy xuất để Admin đánh giá RAG")
public record KnowledgeSearchTestItemResponse(
        String content,
        String category,
        String language,
        List<String> symptoms,
        List<String> serviceCategoryHints,
        List<String> specializationHints,
        String sourceKey,
        String sourceVersion,
        String recordId,
        String title,
        String sourceUrl,
        double score
) {

    public KnowledgeSearchTestItemResponse {
        symptoms = List.copyOf(symptoms);
        serviceCategoryHints = List.copyOf(serviceCategoryHints);
        specializationHints = List.copyOf(specializationHints);
    }
}
