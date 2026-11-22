package com.dentalclinic.ai.orchestration.model;

import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalResult;

import java.util.List;
import java.util.Objects;

public record RagQueryResult(
        boolean available,
        List<KnowledgeRetrievalResult> evidence
) {

    public RagQueryResult {
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence must not be null"));
    }

    public static RagQueryResult notRequired() {
        return new RagQueryResult(true, List.of());
    }

    public static RagQueryResult unavailable() {
        return new RagQueryResult(false, List.of());
    }
}
