package com.dentalclinic.ai.knowledge;

import com.dentalclinic.ai.knowledge.entity.KnowledgeVectorDocument;
import com.dentalclinic.ai.knowledge.mapper.KnowledgeVectorDocumentMapper;
import com.dentalclinic.ai.knowledge.model.EmbeddedKnowledgeChunk;
import com.dentalclinic.ai.knowledge.repository.KnowledgeVectorDocumentRepository;
import com.dentalclinic.ai.knowledge.service.KnowledgeVectorStorageService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeVectorStorageServiceTest {

    private final KnowledgeVectorDocumentRepository repository = mock(
            KnowledgeVectorDocumentRepository.class
    );
    private final KnowledgeVectorDocumentMapper mapper = mock(KnowledgeVectorDocumentMapper.class);
    private final KnowledgeVectorStorageService service = new KnowledgeVectorStorageService(
            repository,
            mapper
    );

    @Test
    void shouldPersistMappedChunksAsOneAtomicBatch() {
        EmbeddedKnowledgeChunk chunk = mock(EmbeddedKnowledgeChunk.class);
        KnowledgeVectorDocument entity = new KnowledgeVectorDocument();
        UUID id = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();
        String contentHash = "a".repeat(64);
        entity.setId(id);
        when(mapper.toStagingEntity(chunk, contentHash, syncId)).thenReturn(entity);
        when(repository.saveAllAndFlush(any())).thenReturn(List.of(entity));

        List<UUID> storedIds = service.storeStaging(List.of(chunk), contentHash, syncId);

        assertThat(storedIds).containsExactly(id);
        verify(repository).saveAllAndFlush(List.of(entity));
    }

    @Test
    void shouldNotCallRepositoryForEmptyChunkList() {
        assertThat(service.storeStaging(
                List.of(),
                "a".repeat(64),
                UUID.randomUUID()
        )).isEmpty();

        verify(repository, never()).saveAllAndFlush(any());
    }

    @Test
    void shouldDeleteOnlyDocumentsOwnedByFailedSync() {
        UUID syncId = UUID.randomUUID();
        when(repository.deleteStagingDocuments("source-key", syncId.toString())).thenReturn(3);

        int deleted = service.deleteStaging("source-key", syncId);

        assertThat(deleted).isEqualTo(3);
        verify(repository).deleteStagingDocuments("source-key", syncId.toString());
    }
}
