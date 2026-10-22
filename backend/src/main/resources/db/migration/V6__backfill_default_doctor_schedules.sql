-- Backfill the shared Monday-Saturday schedule only for doctors who have no
-- weekly schedule at all. Existing partial or customized schedules are kept.

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
        ('AFTERNOON', TIME '13:00', TIME '17:00')
) AS session_time(session, start_time, end_time)
WHERE NOT EXISTS (
    SELECT 1
    FROM doctor_schedules existing_schedule
    WHERE existing_schedule.doctor_id = doctor.id
)
ON CONFLICT (doctor_id, day_of_week, session) DO NOTHING;
