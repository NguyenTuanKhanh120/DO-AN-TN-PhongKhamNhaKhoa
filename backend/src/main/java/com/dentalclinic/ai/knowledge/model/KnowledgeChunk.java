package com.dentalclinic.ai.knowledge.model;

public record KnowledgeChunk(
        String content,
        KnowledgeChunkMetadata metadata
) {
}
