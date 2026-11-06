package com.dentalclinic.ai.knowledge.service;

import com.dentalclinic.ai.knowledge.entity.KnowledgeVectorDocument;
import com.dentalclinic.ai.knowledge.mapper.KnowledgeVectorDocumentMapper;
import com.dentalclinic.ai.knowledge.model.EmbeddedKnowledgeChunk;
import com.dentalclinic.ai.knowledge.repository.KnowledgeVectorDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class KnowledgeVectorStorageService {

    private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");

    private final KnowledgeVectorDocumentRepository repository;
    private final KnowledgeVectorDocumentMapper mapper;

    public KnowledgeVectorStorageService(
            KnowledgeVectorDocumentRepository repository,
            KnowledgeVectorDocumentMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public List<UUID> storeStaging(
            List<EmbeddedKnowledgeChunk> embeddedChunks,
            String contentHash,
            UUID syncId
    ) {
        Objects.requireNonNull(embeddedChunks, "embeddedChunks must not be null");
        Objects.requireNonNull(syncId, "syncId must not be null");
        if (contentHash == null || !SHA_256.matcher(contentHash).matches()) {
            throw new IllegalArgumentException("contentHash must be a lowercase SHA-256 value");
        }
        if (embeddedChunks.isEmpty()) {
            return List.of();
        }
        List<KnowledgeVectorDocument> entities = embeddedChunks.stream()
                .map(chunk -> mapper.toStagingEntity(chunk, contentHash, syncId))
                .toList();
        return repository.saveAllAndFlush(entities).stream()
                .map(KnowledgeVectorDocument::getId)
                .toList();
    }

    @Transactional(readOnly = true)
    public Set<String> findStoredRecordIds(
            String sourceKey,
            String contentHash,
            String embeddingModel,
            int embeddingDimension
    ) {
        Objects.requireNonNull(sourceKey, "sourceKey must not be null");
        Objects.requireNonNull(contentHash, "contentHash must not be null");
        Objects.requireNonNull(embeddingModel, "embeddingModel must not be null");
        return Set.copyOf(repository.findStoredRecordIds(
                sourceKey,
                contentHash,
                embeddingModel,
                Integer.toString(embeddingDimension)
        ));
    }
}
