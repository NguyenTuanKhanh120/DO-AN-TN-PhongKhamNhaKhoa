package com.dentalclinic.ai.knowledge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "knowledge_sources",
        uniqueConstraints = @UniqueConstraint(name = "uq_knowledge_sources_key", columnNames = "source_key")
)
public class KnowledgeSource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "source_key", nullable = false, length = 150)
    private String sourceKey;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 10)
    private KnowledgeFileType fileType;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "version", length = 100)
    private String sourceVersion;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "knowledge_source_status_enum")
    private KnowledgeSourceStatus status = KnowledgeSourceStatus.PENDING;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "embedding_model", length = 255)
    private String embeddingModel;

    @Column(name = "embedding_dimension")
    private Integer embeddingDimension;

    @Column(name = "clinical_review_status", length = 50)
    private String clinicalReviewStatus;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "sync_token")
    private UUID syncToken;

    @Column(name = "pending_content_hash", length = 64)
    private String pendingContentHash;

    @Column(name = "processed_chunk_count", nullable = false)
    private int processedChunkCount;

    @Column(name = "total_chunk_count", nullable = false)
    private int totalChunkCount;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
