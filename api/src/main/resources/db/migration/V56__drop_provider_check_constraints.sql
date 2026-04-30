-- Remove provider CHECK constraints to allow adding new integration providers
-- without requiring a migration for each one. Application-level validation is sufficient.

DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        WHERE rel.relname = 'user_integrations'
          AND con.contype = 'c'
          AND pg_get_constraintdef(con.oid) LIKE '%provider%'
    LOOP
        EXECUTE format('ALTER TABLE user_integrations DROP CONSTRAINT %I', r.conname);
    END LOOP;

    FOR r IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        WHERE rel.relname = 'integration_events'
          AND con.contype = 'c'
          AND pg_get_constraintdef(con.oid) LIKE '%provider%'
    LOOP
        EXECUTE format('ALTER TABLE integration_events DROP CONSTRAINT %I', r.conname);
    END LOOP;
END $$;
