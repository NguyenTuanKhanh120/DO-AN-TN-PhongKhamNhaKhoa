DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM medical_record_drafts LIMIT 1) THEN
        RAISE EXCEPTION
            'Refusing to drop medical_record_drafts because it contains unfinalized patient data. Preserve or review the rows before retrying V8.';
    END IF;
END
$$;

DROP TABLE medical_record_drafts;
