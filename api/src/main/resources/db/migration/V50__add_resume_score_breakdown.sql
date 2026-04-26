ALTER TABLE user_profiles
    ADD COLUMN resume_score_breakdown JSONB NOT NULL DEFAULT '{}';
