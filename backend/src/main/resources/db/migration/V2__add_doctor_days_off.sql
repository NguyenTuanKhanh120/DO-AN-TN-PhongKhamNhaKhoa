-- PHASE 26.1: date-specific doctor days off.
-- Weekly schedules remain unchanged; FULL_DAY is represented by two rows.

CREATE TABLE doctor_days_off (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    doctor_id   UUID NOT NULL,
    off_date    DATE NOT NULL,
    session     session_enum NOT NULL,
    reason      VARCHAR(255) NOT NULL,
    created_by  UUID NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_doctor_days_off_doctor_date_session
        UNIQUE (doctor_id, off_date, session),
    CONSTRAINT fk_doctor_days_off_doctor
        FOREIGN KEY (doctor_id) REFERENCES doctors(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_doctor_days_off_created_by
        FOREIGN KEY (created_by) REFERENCES users(id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_doctor_days_off_reason_not_blank
        CHECK (btrim(reason) <> '')
);

CREATE INDEX ix_doctor_days_off_doctor_date
    ON doctor_days_off (doctor_id, off_date);
