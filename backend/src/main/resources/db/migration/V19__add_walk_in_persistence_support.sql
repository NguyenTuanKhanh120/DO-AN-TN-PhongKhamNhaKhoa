-- Expand the persistence model for patients received directly at the clinic.
-- Patient.full_name remains nullable during this phase so the existing
-- application writers can continue working until the patient domain is updated.

CREATE TYPE booking_source_enum AS ENUM ('ONLINE', 'WALK_IN');

ALTER TABLE patients
    ADD COLUMN full_name VARCHAR(150);

UPDATE patients patient
SET full_name = clinic_user.full_name
FROM users clinic_user
WHERE clinic_user.id = patient.user_id
  AND patient.full_name IS NULL;

DO $$
DECLARE
    missing_patient_name_count BIGINT;
BEGIN
    SELECT count(*)
    INTO missing_patient_name_count
    FROM patients
    WHERE user_id IS NOT NULL
      AND full_name IS NULL;

    IF missing_patient_name_count > 0 THEN
        RAISE EXCEPTION USING
            ERRCODE = 'P0001',
            MESSAGE = format(
                'Refusing to enable walk-in patients because %s linked patient(s) could not be assigned a full name',
                missing_patient_name_count
            );
    END IF;
END
$$;

ALTER TABLE patients
    ALTER COLUMN user_id DROP NOT NULL,
    DROP CONSTRAINT fk_patients_user,
    ADD CONSTRAINT fk_patients_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE SET NULL;

CREATE INDEX ix_patients_phone
    ON patients (phone);

ALTER TABLE appointments
    ADD COLUMN booking_source booking_source_enum;

UPDATE appointments
SET booking_source = 'ONLINE'
WHERE booking_source IS NULL;

ALTER TABLE appointments
    ALTER COLUMN booking_source SET DEFAULT 'ONLINE',
    ALTER COLUMN booking_source SET NOT NULL;

COMMENT ON COLUMN patients.user_id IS
    'Optional login account. NULL identifies a patient profile without a website account.';
COMMENT ON COLUMN patients.full_name IS
    'Patient clinical identity. Backfilled in V19 and contracted to NOT NULL after all writers are migrated.';
COMMENT ON COLUMN appointments.booking_source IS
    'ONLINE for patient self-booking or WALK_IN for same-day appointments created by clinic staff.';
