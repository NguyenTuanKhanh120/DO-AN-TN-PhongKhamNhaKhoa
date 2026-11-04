package com.dentalclinic.ai.knowledge.model;

public record EmbeddedKnowledgeChunk(
        KnowledgeChunk chunk,
        String embeddingModel,
        int embeddingDimension,
        float[] embedding
) {

    public EmbeddedKnowledgeChunk {
        embedding = embedding.clone();
        if (embedding.length != embeddingDimension) {
            throw new IllegalArgumentException("Embedding dimension does not match vector length");
        }
    }

    @Override
    public float[] embedding() {
        return embedding.clone();
    }
}
