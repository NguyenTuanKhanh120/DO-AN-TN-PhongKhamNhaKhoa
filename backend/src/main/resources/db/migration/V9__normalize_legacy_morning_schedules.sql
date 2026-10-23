-- Older demo data used 08:00-12:00 for the morning session. The current
-- scheduling contract ends the morning session at 11:00, so SlotService
-- correctly rejects those legacy rows as unsupported.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM doctor_schedules schedule
        JOIN appointments appointment
          ON appointment.doctor_id = schedule.doctor_id
        WHERE schedule.session = 'MORNING'
          AND schedule.start_time = TIME '08:00'
          AND schedule.end_time = TIME '12:00'
          AND appointment.status IN ('BOOKED', 'CHECKED_IN', 'IN_PROGRESS')
          AND appointment.scheduled_start_at > CURRENT_TIMESTAMP
          AND EXTRACT(
              ISODOW FROM appointment.scheduled_start_at AT TIME ZONE 'Asia/Ho_Chi_Minh'
          ) = schedule.day_of_week
          AND (appointment.scheduled_start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::time = TIME '11:00'
    ) THEN
        RAISE EXCEPTION
            'Refusing to normalize legacy morning schedules because an active future 11:00 appointment would be affected. Review the appointment before retrying V9.';
    END IF;
END
$$;

UPDATE doctor_schedules
SET end_time = TIME '11:00'
WHERE session = 'MORNING'
  AND start_time = TIME '08:00'
  AND end_time = TIME '12:00';
