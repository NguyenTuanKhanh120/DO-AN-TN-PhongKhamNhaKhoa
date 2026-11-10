-- Contract the appointment scheduling model after every application writer has
-- moved to appointment_date + session + queue_number. scheduled_start_at stays
-- as the estimated-start compatibility timestamp used by existing API readers.

DO $$
DECLARE
    incomplete_queue_count BIGINT;
BEGIN
    SELECT count(*)
    INTO incomplete_queue_count
    FROM appointments
    WHERE appointment_date IS NULL
       OR session IS NULL
       OR queue_number IS NULL;

    IF incomplete_queue_count > 0 THEN
        RAISE EXCEPTION USING
            ERRCODE = 'P0001',
            MESSAGE = format(
                'Refusing to enforce the appointment queue contract because %s appointment(s) have an incomplete queue model',
                incomplete_queue_count
            );
    END IF;
END
$$;

ALTER TABLE appointments
    DROP CONSTRAINT ck_appointments_queue_model_complete,
    DROP CONSTRAINT ck_appointments_queue_number,
    ALTER COLUMN appointment_date SET NOT NULL,
    ALTER COLUMN session SET NOT NULL,
    ALTER COLUMN queue_number SET NOT NULL,
    ADD CONSTRAINT ck_appointments_queue_number
        CHECK (queue_number BETWEEN 1 AND 2);

DROP INDEX ux_appointments_doctor_start_active_legacy;

COMMENT ON COLUMN appointments.scheduled_start_at IS
    'Estimated start timestamp retained for API and historical compatibility.';
COMMENT ON COLUMN appointments.appointment_date IS
    'Required clinic-local appointment date for the two-case scheduling model.';
COMMENT ON COLUMN appointments.session IS
    'Required MORNING or AFTERNOON appointment session.';
COMMENT ON COLUMN appointments.queue_number IS
    'Required case order within a session; only 1 or 2.';
