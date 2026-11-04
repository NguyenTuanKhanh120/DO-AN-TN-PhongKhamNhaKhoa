package com.dentalclinic.ai.knowledge.model;

import java.time.Instant;

public record KnowledgeSyncActivation(
        KnowledgeSyncClaim claim,
        String contentHash,
        String sourceVersion,
        int chunkCount,
        String embeddingModel,
        int embeddingDimension,
        String clinicalReviewStatus,
        Instant completedAt
) {
}
