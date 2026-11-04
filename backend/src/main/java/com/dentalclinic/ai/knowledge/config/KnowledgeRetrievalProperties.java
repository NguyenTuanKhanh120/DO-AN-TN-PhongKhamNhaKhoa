package com.dentalclinic.ai.knowledge.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.ai.retrieval")
public record KnowledgeRetrievalProperties(
        @Min(1) int defaultTopK,
        @Min(1) int maxTopK,
        @DecimalMin("0.0") @DecimalMax("1.0") double similarityThreshold
) {

    public KnowledgeRetrievalProperties {
        if (defaultTopK > maxTopK) {
            throw new IllegalArgumentException("Default retrieval Top-K must not exceed maximum Top-K");
        }
    }
}
