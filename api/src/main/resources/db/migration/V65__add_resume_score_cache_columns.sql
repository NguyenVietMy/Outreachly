ALTER TABLE user_profiles
    ADD COLUMN resume_context_hash VARCHAR(64),
    ADD COLUMN resume_scored_at    TIMESTAMPTZ;
