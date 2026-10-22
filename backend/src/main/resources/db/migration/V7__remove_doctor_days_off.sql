DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM doctor_days_off LIMIT 1) THEN
        RAISE EXCEPTION
            'Refusing to drop doctor_days_off because it contains historical data. Preserve or archive the rows before retrying V7.';
    END IF;
END
$$;

DROP TABLE doctor_days_off;
