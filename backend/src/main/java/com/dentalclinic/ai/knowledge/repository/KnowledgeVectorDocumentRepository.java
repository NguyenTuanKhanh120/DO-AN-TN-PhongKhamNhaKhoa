package com.dentalclinic.ai.knowledge.repository;

import com.dentalclinic.ai.knowledge.entity.KnowledgeVectorDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.List;

public interface KnowledgeVectorDocumentRepository
        extends JpaRepository<KnowledgeVectorDocument, UUID> {

    @Query(value = """
            select count(*)
            from vector_store
            where metadata ->> 'sourceKey' = :sourceKey
              and metadata ->> 'contentHash' = :contentHash
              and metadata ->> 'embeddingModel' = :embeddingModel
              and cast(metadata ->> 'embeddingDimension' as integer) = :embeddingDimension
              and vector_dims(embedding) = :embeddingDimension
            """, nativeQuery = true)
    long countValidStagingDocuments(
            @Param("sourceKey") String sourceKey,
            @Param("contentHash") String contentHash,
            @Param("embeddingModel") String embeddingModel,
            @Param("embeddingDimension") int embeddingDimension
    );

    @Query(value = """
            select metadata ->> 'recordId'
            from vector_store
            where metadata ->> 'sourceKey' = :sourceKey
              and metadata ->> 'contentHash' = :contentHash
              and metadata ->> 'embeddingModel' = :embeddingModel
              and metadata ->> 'embeddingDimension' = :embeddingDimension
            order by metadata ->> 'recordId'
            """, nativeQuery = true)
    List<String> findStoredRecordIds(
            @Param("sourceKey") String sourceKey,
            @Param("contentHash") String contentHash,
            @Param("embeddingModel") String embeddingModel,
            @Param("embeddingDimension") String embeddingDimension
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            delete from vector_store
            where metadata ->> 'sourceKey' = :sourceKey
              and (
                  (metadata ->> 'contentHash') is distinct from :activeContentHash
                  or (metadata ->> 'embeddingModel') is distinct from :embeddingModel
                  or (metadata ->> 'embeddingDimension') is distinct from :embeddingDimension
              )
            """, nativeQuery = true)
    int deleteOtherVersions(
            @Param("sourceKey") String sourceKey,
            @Param("activeContentHash") String activeContentHash,
            @Param("embeddingModel") String embeddingModel,
            @Param("embeddingDimension") String embeddingDimension
    );
}
