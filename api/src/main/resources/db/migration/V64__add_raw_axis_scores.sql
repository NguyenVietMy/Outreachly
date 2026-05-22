ALTER TABLE user_profiles ADD COLUMN raw_axis_scores JSONB DEFAULT '{"dsa":0,"projects":0,"systemDesign":0,"coreCs":0,"resume":0}'::jsonb;

UPDATE user_profiles SET raw_axis_scores = axis_scores;
