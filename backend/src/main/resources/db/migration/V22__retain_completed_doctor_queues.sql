-- Completed examinations consume their doctor's case for the whole session.
-- Keep patient/session uniqueness limited to active appointments.
LOCK TABLE appointments IN SHARE ROW EXCLUSIVE MODE;

DO $$
DECLARE
    conflicting_queue_count BIGINT;
BEGIN
    SELECT count(*)
    INTO conflicting_queue_count
    FROM (
        SELECT doctor_id, appointment_date, session, queue_number
        FROM appointments
        WHERE status IN ('BOOKED', 'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED')
        GROUP BY doctor_id, appointment_date, session, queue_number
        HAVING count(*) > 1
    ) conflicting_queues;

    IF conflicting_queue_count > 0 THEN
        RAISE EXCEPTION USING
            ERRCODE = 'P0001',
            MESSAGE = format(
                'Refusing to retain completed doctor queues because %s doctor/date/session/queue group(s) contain conflicting appointments; preserve and resolve historical records before retrying',
                conflicting_queue_count
            );
    END IF;
END
$$;

CREATE UNIQUE INDEX ux_appointments_doctor_queue_occupied
    ON appointments (doctor_id, appointment_date, session, queue_number)
    WHERE status IN ('BOOKED', 'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED');

DROP INDEX ux_appointments_doctor_queue_active;

COMMENT ON INDEX ux_appointments_doctor_queue_occupied IS
    'Completed appointments retain the doctor case; CANCELLED and NO_SHOW release it.';
