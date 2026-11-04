DO $$
DECLARE
    duplicate_group_count BIGINT;
BEGIN
    SELECT count(*)
    INTO duplicate_group_count
    FROM (
        SELECT user_id
        FROM chat_sessions
        GROUP BY user_id
        HAVING count(*) > 1
    ) duplicate_users;

    IF duplicate_group_count > 0 THEN
        RAISE EXCEPTION USING
            ERRCODE = 'P0001',
            MESSAGE = format(
                'Refusing to enforce one chat session per user because %s duplicate user group(s) exist',
                duplicate_group_count
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'chat_sessions'::regclass
          AND conname = 'uq_chat_sessions_user'
    ) THEN
        ALTER TABLE chat_sessions
            ADD CONSTRAINT uq_chat_sessions_user UNIQUE (user_id);
    END IF;
END
$$;
