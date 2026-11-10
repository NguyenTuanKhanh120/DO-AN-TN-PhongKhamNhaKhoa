-- Contract the patient identity column after all application writers have
-- migrated to patients.full_name.

DO $$
DECLARE
    missing_patient_name_count BIGINT;
BEGIN
    SELECT count(*)
    INTO missing_patient_name_count
    FROM patients
    WHERE full_name IS NULL OR btrim(full_name) = '';

    IF missing_patient_name_count > 0 THEN
        RAISE EXCEPTION USING
            ERRCODE = 'P0001',
            MESSAGE = format(
                'Refusing to require patients.full_name because %s patient record(s) have no valid name',
                missing_patient_name_count
            );
    END IF;
END
$$;

ALTER TABLE patients
    ALTER COLUMN full_name SET NOT NULL;
