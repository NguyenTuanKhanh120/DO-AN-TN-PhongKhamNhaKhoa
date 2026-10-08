-- V1__init_schema.sql
-- Optimized demo schema: 12 tables
-- Project: Dental Clinic Management System + AI Chatbot
-- PostgreSQL 16+ / Spring Boot 3.x / Spring Data JPA / Spring AI / pgvector
-- Business timezone: Asia/Ho_Chi_Minh

-- =========================================================
-- 0. EXTENSIONS
-- =========================================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;

-- =========================================================
-- 1. ENUM TYPES
-- =========================================================
DO $$ BEGIN
    CREATE TYPE role_enum AS ENUM ('ADMIN', 'DOCTOR', 'PATIENT');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE user_status_enum AS ENUM ('ACTIVE', 'INACTIVE');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE gender_enum AS ENUM ('MALE', 'FEMALE', 'OTHER', 'UNSPECIFIED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE doctor_status_enum AS ENUM ('ACTIVE', 'INACTIVE', 'ON_LEAVE');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE session_enum AS ENUM ('MORNING', 'AFTERNOON');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE appointment_status_enum AS ENUM (
        'BOOKED', 'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE appointment_action_enum AS ENUM (
        'BOOK', 'RESCHEDULE', 'CANCEL', 'CHECK_IN', 'START_EXAM', 'COMPLETE', 'NO_SHOW'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE chat_sender_enum AS ENUM ('USER', 'ASSISTANT', 'SYSTEM');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- =========================================================
-- 2. COMMON updated_at TRIGGER
-- =========================================================
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- =========================================================
-- 3. USERS
-- Demo auth uses JWT access token only; no refresh_tokens table.
-- =========================================================
CREATE TABLE IF NOT EXISTS users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(150) NOT NULL,
    role            role_enum NOT NULL,
    status          user_status_enum NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lower CHECK (email = lower(email))
);

-- =========================================================
-- 4. PATIENTS
-- =========================================================
CREATE TABLE IF NOT EXISTS patients (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             UUID NOT NULL,
    date_of_birth       DATE,
    gender              gender_enum NOT NULL DEFAULT 'UNSPECIFIED',
    phone               VARCHAR(30),
    address             TEXT,
    medical_history     TEXT,
    allergies           TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_patients_user UNIQUE (user_id),
    CONSTRAINT fk_patients_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE,
    CONSTRAINT ck_patients_date_of_birth
        CHECK (date_of_birth IS NULL OR date_of_birth <= CURRENT_DATE)
);

-- =========================================================
-- 5. DOCTORS
-- =========================================================
CREATE TABLE IF NOT EXISTS doctors (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             UUID NOT NULL,
    specialization      VARCHAR(150) NOT NULL,
    license_number      VARCHAR(100) NOT NULL,
    bio                 TEXT,
    rating              NUMERIC(2,1) NOT NULL DEFAULT 0.0,
    experience_years    INTEGER NOT NULL DEFAULT 0,
    working_status      doctor_status_enum NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_doctors_user UNIQUE (user_id),
    CONSTRAINT uq_doctors_license_number UNIQUE (license_number),
    CONSTRAINT fk_doctors_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE,
    CONSTRAINT ck_doctors_rating CHECK (rating >= 0.0 AND rating <= 5.0),
    CONSTRAINT ck_doctors_experience_years CHECK (experience_years >= 0)
);

-- =========================================================
-- 6. DENTAL SERVICES
-- Current price is the source of truth for chatbot answers.
-- =========================================================
CREATE TABLE IF NOT EXISTS dental_services (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    price           NUMERIC(14,2) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_dental_services_name UNIQUE (name),
    CONSTRAINT ck_dental_services_price CHECK (price >= 0)
);

-- =========================================================
-- 7. DOCTOR SERVICES
-- Minimal N-N mapping. No surrogate id or audit columns needed for demo.
-- =========================================================
CREATE TABLE IF NOT EXISTS doctor_services (
    doctor_id       UUID NOT NULL,
    service_id      UUID NOT NULL,

    CONSTRAINT pk_doctor_services PRIMARY KEY (doctor_id, service_id),
    CONSTRAINT fk_doctor_services_doctor
        FOREIGN KEY (doctor_id) REFERENCES doctors(id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_doctor_services_service
        FOREIGN KEY (service_id) REFERENCES dental_services(id)
        ON DELETE RESTRICT
);

-- =========================================================
-- 8. DOCTOR SCHEDULES
-- Weekly fixed schedule. No override table in the demo version.
-- If a doctor does not work a session, no row is created.
-- =========================================================
CREATE TABLE IF NOT EXISTS doctor_schedules (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    doctor_id       UUID NOT NULL,
    day_of_week     SMALLINT NOT NULL,  -- 1=Monday ... 7=Sunday
    session         session_enum NOT NULL,
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_doctor_schedules_doctor_day_session
        UNIQUE (doctor_id, day_of_week, session),
    CONSTRAINT fk_doctor_schedules_doctor
        FOREIGN KEY (doctor_id) REFERENCES doctors(id)
        ON DELETE CASCADE,
    CONSTRAINT ck_doctor_schedules_day_of_week
        CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_doctor_schedules_time
        CHECK (end_time > start_time)
);

-- =========================================================
-- 9. APPOINTMENTS
-- Fixed 60-minute slot. Persist only the scheduled start timestamp.
-- scheduled end = scheduled_start_at + 60 minutes.
-- =========================================================
CREATE TABLE IF NOT EXISTS appointments (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    patient_id              UUID NOT NULL,
    doctor_id               UUID NOT NULL,
    service_id              UUID NOT NULL,
    previous_appointment_id UUID,

    scheduled_start_at      TIMESTAMPTZ NOT NULL,
    status                  appointment_status_enum NOT NULL DEFAULT 'BOOKED',

    checked_in_at           TIMESTAMPTZ,
    actual_start_at         TIMESTAMPTZ,
    actual_end_at           TIMESTAMPTZ,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_appointments_patient
        FOREIGN KEY (patient_id) REFERENCES patients(id)
        ON DELETE RESTRICT,

    -- The selected doctor must be assigned to the selected service.
    CONSTRAINT fk_appointments_doctor_service
        FOREIGN KEY (doctor_id, service_id)
        REFERENCES doctor_services(doctor_id, service_id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_appointments_previous
        FOREIGN KEY (previous_appointment_id) REFERENCES appointments(id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_appointments_previous_not_self
        CHECK (previous_appointment_id IS NULL OR previous_appointment_id <> id),

    CONSTRAINT ck_appointments_actual_time_order
        CHECK (
            actual_start_at IS NULL
            OR actual_end_at IS NULL
            OR actual_end_at >= actual_start_at
        )
);

-- Cancelled appointments release the slot.
CREATE UNIQUE INDEX IF NOT EXISTS ux_appointments_doctor_start_not_cancelled
    ON appointments (doctor_id, scheduled_start_at)
    WHERE status <> 'CANCELLED';

-- =========================================================
-- 10. APPOINTMENT HISTORIES
-- Compact audit log; variable old/new values are stored in JSONB.
-- =========================================================
CREATE TABLE IF NOT EXISTS appointment_histories (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    appointment_id      UUID NOT NULL,
    action              appointment_action_enum NOT NULL,
    changed_by          UUID,
    reason              VARCHAR(100),
    details             JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_appointment_histories_appointment
        FOREIGN KEY (appointment_id) REFERENCES appointments(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_appointment_histories_changed_by
        FOREIGN KEY (changed_by) REFERENCES users(id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_appointment_histories_details_object
        CHECK (jsonb_typeof(details) = 'object')
);

-- =========================================================
-- 11. MEDICAL RECORDS
-- appointment_id already determines patient + doctor.
-- Prescription is JSONB for demo simplicity.
-- =========================================================
CREATE TABLE IF NOT EXISTS medical_records (
    id                          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    appointment_id              UUID NOT NULL,
    diagnosis                   TEXT NOT NULL,
    treatment                   TEXT NOT NULL,
    treatment_plan              TEXT,
    notes                       TEXT,

    follow_up_required          BOOLEAN NOT NULL,
    recommended_follow_up_date  DATE,
    follow_up_note              TEXT,

    prescription                JSONB NOT NULL DEFAULT '[]'::jsonb,

    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_medical_records_appointment UNIQUE (appointment_id),
    CONSTRAINT fk_medical_records_appointment
        FOREIGN KEY (appointment_id) REFERENCES appointments(id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_medical_records_prescription_array
        CHECK (jsonb_typeof(prescription) = 'array'),
    CONSTRAINT ck_medical_records_follow_up
        CHECK (
            follow_up_required = FALSE
            OR recommended_follow_up_date IS NOT NULL
            OR follow_up_note IS NOT NULL
        )
);

-- =========================================================
-- 12. CHAT SESSIONS
-- Authenticated users only in the demo version.
-- Current symptom/intent context is stored at session level.
-- =========================================================
CREATE TABLE IF NOT EXISTS chat_sessions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL,
    title           VARCHAR(255),
    context_data    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_chat_sessions_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE,
    CONSTRAINT ck_chat_sessions_context_object
        CHECK (jsonb_typeof(context_data) = 'object')
);

-- =========================================================
-- 13. CHAT MESSAGES
-- =========================================================
CREATE TABLE IF NOT EXISTS chat_messages (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id      UUID NOT NULL,
    sender          chat_sender_enum NOT NULL,
    content         TEXT NOT NULL,
    metadata        JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_chat_messages_session
        FOREIGN KEY (session_id) REFERENCES chat_sessions(id)
        ON DELETE CASCADE,
    CONSTRAINT ck_chat_messages_metadata_object
        CHECK (jsonb_typeof(metadata) = 'object')
);

-- =========================================================
-- 14. VECTOR STORE
-- Dimension 768 follows the current project design.
-- Change it before V1 is applied if the selected embedding model differs.
-- =========================================================
CREATE TABLE IF NOT EXISTS vector_store (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    content     TEXT NOT NULL,
    metadata    JSONB NOT NULL DEFAULT '{}'::jsonb,
    embedding   vector(768) NOT NULL,

    CONSTRAINT ck_vector_store_metadata_object
        CHECK (jsonb_typeof(metadata) = 'object')
);

-- =========================================================
-- 15. INDEXES
-- =========================================================
CREATE INDEX IF NOT EXISTS ix_doctors_specialization
    ON doctors (specialization);

CREATE INDEX IF NOT EXISTS ix_doctors_working_status
    ON doctors (working_status);

CREATE INDEX IF NOT EXISTS ix_dental_services_active
    ON dental_services (is_active);

CREATE INDEX IF NOT EXISTS ix_dental_services_name_lower
    ON dental_services (lower(name));

CREATE INDEX IF NOT EXISTS ix_doctor_services_service_doctor
    ON doctor_services (service_id, doctor_id);

CREATE INDEX IF NOT EXISTS ix_doctor_schedules_lookup
    ON doctor_schedules (doctor_id, day_of_week, session);

CREATE INDEX IF NOT EXISTS ix_appointments_doctor_start_status
    ON appointments (doctor_id, scheduled_start_at, status);

CREATE INDEX IF NOT EXISTS ix_appointments_patient_history
    ON appointments (patient_id, scheduled_start_at DESC);

CREATE INDEX IF NOT EXISTS ix_appointments_previous
    ON appointments (previous_appointment_id);

CREATE INDEX IF NOT EXISTS ix_appointment_histories_appointment_created
    ON appointment_histories (appointment_id, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_appointment_histories_details_gin
    ON appointment_histories USING GIN (details);

CREATE INDEX IF NOT EXISTS ix_chat_sessions_user_updated
    ON chat_sessions (user_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS ix_chat_sessions_context_gin
    ON chat_sessions USING GIN (context_data);

CREATE INDEX IF NOT EXISTS ix_chat_messages_session_created
    ON chat_messages (session_id, created_at);

CREATE INDEX IF NOT EXISTS ix_chat_messages_metadata_gin
    ON chat_messages USING GIN (metadata);

CREATE INDEX IF NOT EXISTS ix_vector_store_metadata_gin
    ON vector_store USING GIN (metadata);

CREATE INDEX IF NOT EXISTS ix_vector_store_embedding_hnsw
    ON vector_store USING hnsw (embedding vector_cosine_ops);

-- =========================================================
-- 16. updated_at TRIGGERS
-- =========================================================
DROP TRIGGER IF EXISTS trg_users_updated_at ON users;
CREATE TRIGGER trg_users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_patients_updated_at ON patients;
CREATE TRIGGER trg_patients_updated_at
BEFORE UPDATE ON patients
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_doctors_updated_at ON doctors;
CREATE TRIGGER trg_doctors_updated_at
BEFORE UPDATE ON doctors
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_dental_services_updated_at ON dental_services;
CREATE TRIGGER trg_dental_services_updated_at
BEFORE UPDATE ON dental_services
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_doctor_schedules_updated_at ON doctor_schedules;
CREATE TRIGGER trg_doctor_schedules_updated_at
BEFORE UPDATE ON doctor_schedules
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_appointments_updated_at ON appointments;
CREATE TRIGGER trg_appointments_updated_at
BEFORE UPDATE ON appointments
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_medical_records_updated_at ON medical_records;
CREATE TRIGGER trg_medical_records_updated_at
BEFORE UPDATE ON medical_records
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_chat_sessions_updated_at ON chat_sessions;
CREATE TRIGGER trg_chat_sessions_updated_at
BEFORE UPDATE ON chat_sessions
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
