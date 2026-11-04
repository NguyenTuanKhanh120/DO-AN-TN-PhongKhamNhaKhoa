package com.dentalclinic.ai.knowledge.repository;

import com.dentalclinic.ai.knowledge.entity.KnowledgeVectorDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface KnowledgeVectorDocumentRepository
        extends JpaRepository<KnowledgeVectorDocument, UUID> {

    @Query(value = """
            select count(*)
            from vector_store
            where metadata ->> 'sourceKey' = :sourceKey
              and metadata ->> 'contentHash' = :contentHash
              and metadata ->> 'syncId' = :syncId
              and cast(metadata ->> 'embeddingDimension' as integer) = :embeddingDimension
              and vector_dims(embedding) = :embeddingDimension
            """, nativeQuery = true)
    long countValidStagingDocuments(
            @Param("sourceKey") String sourceKey,
            @Param("contentHash") String contentHash,
            @Param("syncId") String syncId,
            @Param("embeddingDimension") int embeddingDimension
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            delete from vector_store
            where metadata ->> 'sourceKey' = :sourceKey
              and (metadata ->> 'syncId') is distinct from :activeSyncId
            """, nativeQuery = true)
    int deleteOtherVersions(
            @Param("sourceKey") String sourceKey,
            @Param("activeSyncId") String activeSyncId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            delete from vector_store
            where metadata ->> 'sourceKey' = :sourceKey
              and metadata ->> 'syncId' = :syncId
            """, nativeQuery = true)
    int deleteStagingDocuments(
            @Param("sourceKey") String sourceKey,
            @Param("syncId") String syncId
    );
}
