-- Standardize every doctor on the shared Monday-Saturday schedule used by the
-- two-case scheduling policy. Sunday remains NOT_WORKING.

DO $$
DECLARE
    affected_appointment_count BIGINT;
BEGIN
    SELECT count(*)
    INTO affected_appointment_count
    FROM appointments appointment
    WHERE appointment.status IN ('BOOKED', 'CHECKED_IN', 'IN_PROGRESS')
      AND appointment.scheduled_start_at > CURRENT_TIMESTAMP
      AND (
          EXTRACT(
              ISODOW FROM appointment.scheduled_start_at AT TIME ZONE 'Asia/Ho_Chi_Minh'
          ) = 7
          OR NOT (
              (
                  (appointment.scheduled_start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::time
                      >= TIME '08:00'
                  AND (appointment.scheduled_start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::time
                      < TIME '11:00'
              )
              OR (
                  (appointment.scheduled_start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::time
                      >= TIME '14:00'
                  AND (appointment.scheduled_start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::time
                      < TIME '17:00'
              )
          )
      );

    IF affected_appointment_count > 0 THEN
        RAISE EXCEPTION USING
            ERRCODE = 'P0001',
            MESSAGE = format(
                'Refusing to standardize doctor schedules because %s active future appointment(s) fall outside the new Monday-Saturday 08:00-11:00 and 14:00-17:00 sessions',
                affected_appointment_count
            );
    END IF;
END
$$;

DELETE FROM doctor_schedules
WHERE day_of_week = 7;

UPDATE doctor_schedules
SET start_time = CASE session
        WHEN 'MORNING' THEN TIME '08:00'
        WHEN 'AFTERNOON' THEN TIME '14:00'
    END,
    end_time = CASE session
        WHEN 'MORNING' THEN TIME '11:00'
        WHEN 'AFTERNOON' THEN TIME '17:00'
    END
WHERE day_of_week BETWEEN 1 AND 6;

INSERT INTO doctor_schedules (
    doctor_id,
    day_of_week,
    session,
    start_time,
    end_time
)
SELECT
    doctor.id,
    working_day.day_of_week,
    session_time.session::session_enum,
    session_time.start_time,
    session_time.end_time
FROM doctors doctor
CROSS JOIN generate_series(1, 6) AS working_day(day_of_week)
CROSS JOIN (
    VALUES
        ('MORNING', TIME '08:00', TIME '11:00'),
        ('AFTERNOON', TIME '14:00', TIME '17:00')
) AS session_time(session, start_time, end_time)
ON CONFLICT (doctor_id, day_of_week, session) DO NOTHING;

ALTER TABLE doctor_schedules
    DROP CONSTRAINT ck_doctor_schedules_day_of_week,
    ADD CONSTRAINT ck_doctor_schedules_day_of_week
        CHECK (day_of_week BETWEEN 1 AND 6);
