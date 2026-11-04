package com.dentalclinic.ai.knowledge.model;

import java.util.UUID;

import static java.util.Objects.requireNonNull;

public record KnowledgeSyncClaim(
        String sourceKey,
        UUID syncToken,
        String previousContentHash
) {

    public KnowledgeSyncClaim {
        if (sourceKey == null || sourceKey.isBlank()) {
            throw new IllegalArgumentException("sourceKey must not be blank");
        }
        syncToken = requireNonNull(syncToken, "syncToken must not be null");
    }
}
