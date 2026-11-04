DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type
        WHERE typname = 'knowledge_source_status_enum'
    ) THEN
        CREATE TYPE knowledge_source_status_enum AS ENUM (
            'PENDING',
            'SYNCING',
            'SUCCESS',
            'FAILED'
        );
    END IF;
END
$$;

CREATE TABLE knowledge_sources (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_key              VARCHAR(150) NOT NULL,
    file_name               VARCHAR(255) NOT NULL,
    file_type               VARCHAR(10) NOT NULL,
    content_hash            VARCHAR(64),
    version                 VARCHAR(100),
    status                  knowledge_source_status_enum NOT NULL DEFAULT 'PENDING',
    chunk_count             INTEGER NOT NULL DEFAULT 0,
    embedding_model         VARCHAR(255),
    embedding_dimension     INTEGER,
    clinical_review_status  VARCHAR(50),
    last_synced_at          TIMESTAMPTZ,
    last_success_at         TIMESTAMPTZ,
    last_error              TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_knowledge_sources_key UNIQUE (source_key),
    CONSTRAINT ck_knowledge_sources_key_not_blank
        CHECK (btrim(source_key) <> ''),
    CONSTRAINT ck_knowledge_sources_file_name_not_blank
        CHECK (btrim(file_name) <> ''),
    CONSTRAINT ck_knowledge_sources_file_type
        CHECK (file_type IN ('JSON', 'CSV')),
    CONSTRAINT ck_knowledge_sources_content_hash
        CHECK (content_hash IS NULL OR content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_knowledge_sources_version_not_blank
        CHECK (version IS NULL OR btrim(version) <> ''),
    CONSTRAINT ck_knowledge_sources_chunk_count
        CHECK (chunk_count >= 0),
    CONSTRAINT ck_knowledge_sources_embedding_dimension
        CHECK (embedding_dimension IS NULL OR embedding_dimension > 0)
);

CREATE INDEX ix_knowledge_sources_status
    ON knowledge_sources (status, updated_at DESC);

CREATE INDEX ix_knowledge_sources_file_name
    ON knowledge_sources (file_name);

CREATE TRIGGER trg_knowledge_sources_updated_at
BEFORE UPDATE ON knowledge_sources
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
