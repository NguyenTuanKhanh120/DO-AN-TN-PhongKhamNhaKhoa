-- PHASE 26.4: server-side login sessions, rotating refresh tokens and
-- PostgreSQL-backed medical-record drafts.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'auth_session_status_enum') THEN
        CREATE TYPE auth_session_status_enum AS ENUM ('ACTIVE', 'LOCKED', 'REVOKED');
    END IF;
END
$$;

CREATE TABLE auth_sessions (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id                 UUID NOT NULL,
    role                    role_enum NOT NULL,
    status                  auth_session_status_enum NOT NULL DEFAULT 'ACTIVE',
    logged_in_at            TIMESTAMPTZ NOT NULL,
    absolute_expires_at     TIMESTAMPTZ NOT NULL,
    last_activity_at        TIMESTAMPTZ NOT NULL,
    idle_timeout_seconds    INTEGER,
    csrf_token_hash         VARCHAR(64) NOT NULL,
    locked_at               TIMESTAMPTZ,
    revoked_at              TIMESTAMPTZ,
    revocation_reason       VARCHAR(100),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_auth_sessions_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE,
    CONSTRAINT ck_auth_sessions_absolute_expiry
        CHECK (absolute_expires_at > logged_in_at),
    CONSTRAINT ck_auth_sessions_last_activity
        CHECK (last_activity_at >= logged_in_at),
    CONSTRAINT ck_auth_sessions_idle_timeout
        CHECK (idle_timeout_seconds IS NULL OR idle_timeout_seconds > 0),
    CONSTRAINT ck_auth_sessions_role_policy
        CHECK (
            (role = 'ADMIN'
                AND idle_timeout_seconds = 900
                AND absolute_expires_at = logged_in_at + INTERVAL '8 hours')
            OR (role IN ('DOCTOR', 'STAFF')
                AND idle_timeout_seconds = 1200
                AND absolute_expires_at = logged_in_at + INTERVAL '12 hours')
            OR (role = 'PATIENT'
                AND idle_timeout_seconds IS NULL
                AND absolute_expires_at = logged_in_at + INTERVAL '7 days')
        ),
    CONSTRAINT ck_auth_sessions_locked_metadata
        CHECK (status <> 'LOCKED' OR locked_at IS NOT NULL),
    CONSTRAINT ck_auth_sessions_revoked_metadata
        CHECK (status <> 'REVOKED' OR revoked_at IS NOT NULL)
);

CREATE TABLE refresh_tokens (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id              UUID NOT NULL,
    token_hash              VARCHAR(64) NOT NULL,
    issued_at               TIMESTAMPTZ NOT NULL,
    expires_at              TIMESTAMPTZ NOT NULL,
    consumed_at             TIMESTAMPTZ,
    revoked_at              TIMESTAMPTZ,
    replaced_by_token_id    UUID,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_session
        FOREIGN KEY (session_id) REFERENCES auth_sessions(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_refresh_tokens_replacement
        FOREIGN KEY (replaced_by_token_id) REFERENCES refresh_tokens(id)
        ON DELETE SET NULL,
    CONSTRAINT ck_refresh_tokens_expiry
        CHECK (expires_at > issued_at),
    CONSTRAINT ck_refresh_tokens_consumed_after_issue
        CHECK (consumed_at IS NULL OR consumed_at >= issued_at),
    CONSTRAINT ck_refresh_tokens_revoked_after_issue
        CHECK (revoked_at IS NULL OR revoked_at >= issued_at)
);

CREATE UNIQUE INDEX ux_refresh_tokens_session_current
    ON refresh_tokens (session_id)
    WHERE consumed_at IS NULL AND revoked_at IS NULL;

CREATE INDEX ix_auth_sessions_user_status
    ON auth_sessions (user_id, status, absolute_expires_at DESC);

CREATE INDEX ix_auth_sessions_expiry
    ON auth_sessions (absolute_expires_at)
    WHERE status <> 'REVOKED';

CREATE INDEX ix_refresh_tokens_session_history
    ON refresh_tokens (session_id, issued_at DESC);

CREATE TABLE medical_record_drafts (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    appointment_id  UUID NOT NULL,
    draft_data      JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_medical_record_drafts_appointment UNIQUE (appointment_id),
    CONSTRAINT fk_medical_record_drafts_appointment
        FOREIGN KEY (appointment_id) REFERENCES appointments(id)
        ON DELETE CASCADE,
    CONSTRAINT ck_medical_record_drafts_object
        CHECK (jsonb_typeof(draft_data) = 'object')
);

CREATE TRIGGER trg_auth_sessions_updated_at
BEFORE UPDATE ON auth_sessions
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_medical_record_drafts_updated_at
BEFORE UPDATE ON medical_record_drafts
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
