-- Hibernate's former UTC JDBC calendar shifted LocalTime schedule values by
-- seven hours before PostgreSQL stored them in TIME WITHOUT TIME ZONE columns.
-- Move only values that fall in the shifted morning/afternoon windows; rows
-- inserted directly by V6 already use clinic-local time and remain unchanged.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM doctor_schedules schedule
        JOIN appointments appointment
          ON appointment.doctor_id = schedule.doctor_id
        WHERE schedule.session = 'MORNING'
          AND schedule.start_time >= TIME '01:00'
          AND schedule.end_time = TIME '05:00'
          AND appointment.status IN ('BOOKED', 'CHECKED_IN', 'IN_PROGRESS')
          AND appointment.scheduled_start_at > CURRENT_TIMESTAMP
          AND EXTRACT(
              ISODOW FROM appointment.scheduled_start_at AT TIME ZONE 'Asia/Ho_Chi_Minh'
          ) = schedule.day_of_week
          AND (appointment.scheduled_start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::time = TIME '11:00'
    ) THEN
        RAISE EXCEPTION
            'Refusing to repair shifted morning schedules because an active future 11:00 appointment would be affected. Review the appointment before retrying V10.';
    END IF;
END
$$;

UPDATE doctor_schedules
SET start_time = start_time + INTERVAL '7 hours',
    end_time = LEAST(end_time + INTERVAL '7 hours', TIME '11:00')
WHERE session = 'MORNING'
  AND start_time >= TIME '01:00'
  AND start_time < TIME '05:00'
  AND end_time > TIME '01:00'
  AND end_time <= TIME '05:00';

UPDATE doctor_schedules
SET start_time = start_time + INTERVAL '7 hours',
    end_time = end_time + INTERVAL '7 hours'
WHERE session = 'AFTERNOON'
  AND start_time >= TIME '06:00'
  AND start_time < TIME '10:00'
  AND end_time > TIME '06:00'
  AND end_time <= TIME '10:00';
