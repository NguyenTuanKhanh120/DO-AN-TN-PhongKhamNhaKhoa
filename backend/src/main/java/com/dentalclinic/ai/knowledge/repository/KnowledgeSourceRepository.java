package com.dentalclinic.ai.knowledge.repository;

import com.dentalclinic.ai.knowledge.entity.KnowledgeSource;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeSourceRepository extends JpaRepository<KnowledgeSource, UUID> {

    List<KnowledgeSource> findAllByOrderBySourceKeyAsc();

    Optional<KnowledgeSource> findBySourceKey(String sourceKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select source from KnowledgeSource source where source.sourceKey = :sourceKey")
    Optional<KnowledgeSource> findBySourceKeyForUpdate(@Param("sourceKey") String sourceKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into knowledge_sources (
                id,
                source_key,
                file_name,
                file_type,
                status,
                chunk_count,
                last_synced_at,
                sync_token
            ) values (
                uuid_generate_v4(),
                :sourceKey,
                :fileName,
                :fileType,
                cast('SYNCING' as knowledge_source_status_enum),
                0,
                :attemptedAt,
                :syncToken
            )
            on conflict (source_key) do update
            set file_name = excluded.file_name,
                file_type = excluded.file_type,
                status = cast('SYNCING' as knowledge_source_status_enum),
                last_synced_at = excluded.last_synced_at,
                last_error = null,
                sync_token = excluded.sync_token
            where knowledge_sources.status <> cast('SYNCING' as knowledge_source_status_enum)
               or knowledge_sources.last_synced_at is null
               or knowledge_sources.last_synced_at < :staleBefore
            """, nativeQuery = true)
    int tryBeginSync(
            @Param("sourceKey") String sourceKey,
            @Param("fileName") String fileName,
            @Param("fileType") String fileType,
            @Param("attemptedAt") Instant attemptedAt,
            @Param("staleBefore") Instant staleBefore,
            @Param("syncToken") UUID syncToken
    );
}
