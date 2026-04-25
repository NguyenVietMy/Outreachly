ALTER TABLE user_profiles
    ADD COLUMN target_role VARCHAR(20) DEFAULT 'swe_intern',
    ADD COLUMN graduation_year INT;
