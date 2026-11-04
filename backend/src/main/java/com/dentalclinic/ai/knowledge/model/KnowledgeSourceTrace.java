package com.dentalclinic.ai.knowledge.model;

public record KnowledgeSourceTrace(
        String sourceKey,
        String sourceVersion,
        String recordId,
        String title,
        String sourceUrl
) {
}
