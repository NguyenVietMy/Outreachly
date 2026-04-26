ALTER TABLE user_integrations
    ADD COLUMN auto_sync_enabled BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN consecutive_failures INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_sync_after TIMESTAMP,
    ADD COLUMN last_sync_duration_ms BIGINT;
