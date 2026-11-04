package com.dentalclinic.ai.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Kết quả kiểm thử truy xuất vector từ kho tri thức")
public record KnowledgeSearchTestResponse(
        String query,
        int topK,
        double minimumSimilarity,
        int resultCount,
        List<KnowledgeSearchTestItemResponse> results
) {

    public KnowledgeSearchTestResponse {
        results = List.copyOf(results);
    }
}
