-- Prepare appointments for the two-case-per-session scheduling model.
-- scheduled_start_at remains the compatibility timestamp until all application
-- reads and writes have moved to appointment_date + session + queue_number.

ALTER TABLE appointments
    ADD COLUMN appointment_date DATE,
    ADD COLUMN session session_enum,
    ADD COLUMN queue_number SMALLINT;

DO $$
DECLARE
    unsupported_start_count BIGINT;
    doctor_session_conflict_count BIGINT;
    patient_session_conflict_count BIGINT;
BEGIN
    SELECT count(*)
    INTO unsupported_start_count
    FROM appointments appointment
    WHERE NOT (
        (appointment.scheduled_start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::time
            >= TIME '08:00'
        AND (appointment.scheduled_start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::time
            < TIME '12:00'
        OR (appointment.scheduled_start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::time
            >= TIME '13:00'
        AND (appointment.scheduled_start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::time
            < TIME '17:00'
    );

    IF unsupported_start_count > 0 THEN
        RAISE EXCEPTION USING
            ERRCODE = 'P0001',
            MESSAGE = format(
                'Refusing to backfill the appointment queue model because %s appointment(s) have a start time outside the supported legacy sessions',
                unsupported_start_count
            );
    END IF;

    SELECT count(*)
    INTO doctor_session_conflict_count
    FROM (
        SELECT
            appointment.doctor_id,
            (appointment.scheduled_start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,
            CASE
                WHEN (appointment.scheduled_start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::time
                    < TIME '12:00'
                    THEN 'MORNING'
                ELSE 'AFTERNOON'
            END
        FROM appointments appointment
        WHERE appointment.status IN ('BOOKED', 'CHECKED_IN', 'IN_PROGRESS')
        GROUP BY
            appointment.doctor_id,
            (appointment.scheduled_start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,
            CASE
                WHEN (appointment.scheduled_start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::time
                    < TIME '12:00'
                    THEN 'MORNING'
                ELSE 'AFTERNOON'
            END
        HAVING count(*) > 2
    ) conflicting_doctor_sessions;

    IF doctor_session_conflict_count > 0 THEN
        RAISE EXCEPTION USING
            ERRCODE = 'P0001',
            MESSAGE = format(
                'Refusing to backfill the appointment queue model because %s doctor/date/session group(s) contain more than two active appointments',
                doctor_session_conflict_count
            );
    END IF;

    SELECT count(*)
    INTO patient_session_conflict_count
    FROM (
        SELECT
            appointment.patient_id,
            (appointment.scheduled_start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,
            CASE
                WHEN (appointment.scheduled_start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::time
                    < TIME '12:00'
                    THEN 'MORNING'
                ELSE 'AFTERNOON'
            END
        FROM appointments appointment
        WHERE appointment.status IN ('BOOKED', 'CHECKED_IN', 'IN_PROGRESS')
        GROUP BY
            appointment.patient_id,
            (appointment.scheduled_start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,
            CASE
                WHEN (appointment.scheduled_start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::time
                    < TIME '12:00'
                    THEN 'MORNING'
                ELSE 'AFTERNOON'
            END
        HAVING count(*) > 1
    ) conflicting_patient_sessions;

    IF patient_session_conflict_count > 0 THEN
        RAISE EXCEPTION USING
            ERRCODE = 'P0001',
            MESSAGE = format(
                'Refusing to backfill the appointment queue model because %s patient/date/session group(s) contain more than one active appointment',
                patient_session_conflict_count
            );
    END IF;
END
$$;

WITH classified AS (
    SELECT
        appointment.id,
        appointment.doctor_id,
        appointment.status,
        appointment.scheduled_start_at,
        (appointment.scheduled_start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
            AS appointment_date,
        CASE
            WHEN (appointment.scheduled_start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::time
                < TIME '12:00'
                THEN 'MORNING'::session_enum
            ELSE 'AFTERNOON'::session_enum
        END AS session,
        (appointment.scheduled_start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::time
            AS local_start_time
    FROM appointments appointment
),
active_ranked AS (
    SELECT
        classified.id,
        count(*) OVER (
            PARTITION BY classified.doctor_id,
                         classified.appointment_date,
                         classified.session
        ) AS active_count,
        row_number() OVER (
            PARTITION BY classified.doctor_id,
                         classified.appointment_date,
                         classified.session
            ORDER BY classified.scheduled_start_at, classified.id
        ) AS active_queue_number
    FROM classified
    WHERE classified.status IN ('BOOKED', 'CHECKED_IN', 'IN_PROGRESS')
),
resolved AS (
    SELECT
        classified.id,
        classified.appointment_date,
        classified.session,
        CASE
            WHEN active_ranked.active_count > 1
                THEN active_ranked.active_queue_number
            WHEN classified.session = 'MORNING'
                 AND classified.local_start_time < TIME '09:30'
                THEN 1
            WHEN classified.session = 'AFTERNOON'
                 AND classified.local_start_time < TIME '15:30'
                THEN 1
            ELSE 2
        END::SMALLINT AS queue_number
    FROM classified
    LEFT JOIN active_ranked ON active_ranked.id = classified.id
)
UPDATE appointments appointment
SET appointment_date = resolved.appointment_date,
    session = resolved.session,
    queue_number = resolved.queue_number
FROM resolved
WHERE resolved.id = appointment.id;

-- During the expand phase, legacy application writes may still omit all three
-- new fields. Partial values are never valid, and populated queues are limited
-- to CA_1 or CA_2. A later contract migration can make the fields NOT NULL once
-- all application writers have switched to the queue model.
ALTER TABLE appointments
    ADD CONSTRAINT ck_appointments_queue_number
        CHECK (queue_number IS NULL OR queue_number BETWEEN 1 AND 2),
    ADD CONSTRAINT ck_appointments_queue_model_complete
        CHECK (
            (appointment_date IS NULL AND session IS NULL AND queue_number IS NULL)
            OR
            (appointment_date IS NOT NULL AND session IS NOT NULL AND queue_number IS NOT NULL)
        );

DROP INDEX ux_appointments_doctor_start_not_cancelled;

-- Compatibility protection for writes made by the pre-queue application.
CREATE UNIQUE INDEX ux_appointments_doctor_start_active_legacy
    ON appointments (doctor_id, scheduled_start_at)
    WHERE appointment_date IS NULL
      AND session IS NULL
      AND queue_number IS NULL
      AND status IN ('BOOKED', 'CHECKED_IN', 'IN_PROGRESS');

CREATE UNIQUE INDEX ux_appointments_doctor_queue_active
    ON appointments (doctor_id, appointment_date, session, queue_number)
    WHERE appointment_date IS NOT NULL
      AND session IS NOT NULL
      AND queue_number IS NOT NULL
      AND status IN ('BOOKED', 'CHECKED_IN', 'IN_PROGRESS');

CREATE UNIQUE INDEX ux_appointments_patient_session_active
    ON appointments (patient_id, appointment_date, session)
    WHERE appointment_date IS NOT NULL
      AND session IS NOT NULL
      AND queue_number IS NOT NULL
      AND status IN ('BOOKED', 'CHECKED_IN', 'IN_PROGRESS');

COMMENT ON COLUMN appointments.appointment_date IS
    'Clinic-local appointment date for the two-case scheduling model.';
COMMENT ON COLUMN appointments.session IS
    'MORNING or AFTERNOON appointment session.';
COMMENT ON COLUMN appointments.queue_number IS
    'Case order within a session; only 1 or 2 when the queue model is populated.';
