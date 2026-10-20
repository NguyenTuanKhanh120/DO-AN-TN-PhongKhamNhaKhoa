-- PHASE 26.3: staff role and patient self-booked follow-up safeguards.
-- CHECKED_IN remains in the PostgreSQL enums for historical compatibility,
-- but new examinations transition directly from BOOKED to IN_PROGRESS.

ALTER TYPE role_enum ADD VALUE IF NOT EXISTS 'STAFF';

-- Enforce the new rule for new/updated rows without invalidating historical
-- records that may have required follow-up with a note but no suggested date.
ALTER TABLE medical_records
    ADD CONSTRAINT ck_medical_records_follow_up_date_required
    CHECK (
        follow_up_required = FALSE
        OR recommended_follow_up_date IS NOT NULL
    ) NOT VALID;

-- One active follow-up may be created from a single completed examination.
-- CANCELLED and NO_SHOW attempts release the recommendation for rebooking;
-- a completed follow-up should use its own medical record for any next visit.
CREATE UNIQUE INDEX ux_appointments_active_follow_up_previous
    ON appointments (previous_appointment_id)
    WHERE previous_appointment_id IS NOT NULL
      AND status IN ('BOOKED', 'CHECKED_IN', 'IN_PROGRESS');
