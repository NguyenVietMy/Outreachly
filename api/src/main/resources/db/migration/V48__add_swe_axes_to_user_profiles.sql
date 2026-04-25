ALTER TABLE user_profiles
    ADD COLUMN leetcode_username VARCHAR(50),
    ADD COLUMN leetcode_stats JSONB NOT NULL DEFAULT '{}',
    ADD COLUMN leetcode_fetched_at TIMESTAMPTZ,
    ADD COLUMN resume_text TEXT NOT NULL DEFAULT '',
    ADD COLUMN resume_filename VARCHAR(255),
    ADD COLUMN resume_uploaded_at TIMESTAMPTZ,
    ADD COLUMN system_design_answers JSONB NOT NULL DEFAULT '{}',
    ADD COLUMN core_cs_answers JSONB NOT NULL DEFAULT '{}',
    ADD COLUMN axis_scores JSONB NOT NULL DEFAULT '{"dsa":0,"projects":0,"systemDesign":0,"coreCs":0,"resume":0}';
