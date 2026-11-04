package com.dentalclinic.ai.recommendation.model;

import com.dentalclinic.ai.knowledge.model.KnowledgeSourceTrace;

import java.util.List;
import java.util.Objects;

public record ServiceDoctorRecommendationResult(
        List<String> serviceCategoryHints,
        List<ServiceDoctorRecommendation> recommendations,
        List<KnowledgeSourceTrace> sources
) {

    public ServiceDoctorRecommendationResult {
        serviceCategoryHints = List.copyOf(Objects.requireNonNull(
                serviceCategoryHints,
                "serviceCategoryHints must not be null"
        ));
        recommendations = List.copyOf(Objects.requireNonNull(
                recommendations,
                "recommendations must not be null"
        ));
        sources = List.copyOf(Objects.requireNonNull(sources, "sources must not be null"));
    }
}
