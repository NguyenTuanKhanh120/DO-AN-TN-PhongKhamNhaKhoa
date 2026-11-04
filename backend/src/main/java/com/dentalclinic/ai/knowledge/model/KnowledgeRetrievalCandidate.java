package com.dentalclinic.ai.knowledge.model;

import java.util.UUID;

public record KnowledgeRetrievalCandidate(
        UUID documentId,
        String content,
        String metadataJson,
        double score
) {
}
