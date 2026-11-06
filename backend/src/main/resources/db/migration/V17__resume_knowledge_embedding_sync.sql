ALTER TABLE knowledge_sources
    ADD COLUMN pending_content_hash VARCHAR(64),
    ADD COLUMN processed_chunk_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN total_chunk_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE knowledge_sources
    ADD CONSTRAINT ck_knowledge_sources_pending_content_hash
        CHECK (pending_content_hash IS NULL OR pending_content_hash ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT ck_knowledge_sources_sync_progress
        CHECK (
            processed_chunk_count >= 0
            AND total_chunk_count >= 0
            AND processed_chunk_count <= total_chunk_count
        );

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM vector_store
        WHERE metadata ? 'sourceKey'
          AND metadata ? 'contentHash'
          AND metadata ? 'embeddingModel'
          AND metadata ? 'embeddingDimension'
          AND metadata ? 'recordId'
        GROUP BY
            metadata ->> 'sourceKey',
            metadata ->> 'contentHash',
            metadata ->> 'embeddingModel',
            metadata ->> 'embeddingDimension',
            metadata ->> 'recordId'
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot enable resumable knowledge sync because duplicate vector records exist';
    END IF;
END
$$;

CREATE UNIQUE INDEX ux_vector_store_source_version_record
    ON vector_store (
        (metadata ->> 'sourceKey'),
        (metadata ->> 'contentHash'),
        (metadata ->> 'embeddingModel'),
        (metadata ->> 'embeddingDimension'),
        (metadata ->> 'recordId')
    )
    WHERE metadata ? 'sourceKey'
      AND metadata ? 'contentHash'
      AND metadata ? 'embeddingModel'
      AND metadata ? 'embeddingDimension'
      AND metadata ? 'recordId';
