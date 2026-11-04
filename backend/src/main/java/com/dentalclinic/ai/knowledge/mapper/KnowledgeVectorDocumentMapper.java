package com.dentalclinic.ai.knowledge.mapper;

import com.dentalclinic.ai.knowledge.entity.KnowledgeVectorDocument;
import com.dentalclinic.ai.knowledge.model.EmbeddedKnowledgeChunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class KnowledgeVectorDocumentMapper {

    private final ObjectMapper objectMapper;

    public KnowledgeVectorDocumentMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public KnowledgeVectorDocument toStagingEntity(
            EmbeddedKnowledgeChunk embeddedChunk,
            String contentHash,
            UUID syncId
    ) {
        ObjectNode metadata = objectMapper.valueToTree(embeddedChunk.chunk().metadata());
        metadata.put("embeddingModel", embeddedChunk.embeddingModel());
        metadata.put("embeddingDimension", embeddedChunk.embeddingDimension());
        metadata.put("contentHash", contentHash);
        metadata.put("syncId", syncId.toString());

        KnowledgeVectorDocument entity = new KnowledgeVectorDocument();
        entity.setContent(embeddedChunk.chunk().content());
        entity.setMetadata(metadata);
        entity.setEmbedding(embeddedChunk.embedding());
        return entity;
    }
}
